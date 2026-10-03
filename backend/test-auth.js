process.env.NODE_ENV = "test";
process.env.RESEND_COOLDOWN_SECONDS = "2"; // 2 seconds for test suite speed
process.env.OTP_EXPIRY_MINUTES = "5";

const app = require("./server");
const http = require("http");

let server;
const PORT = 5599;

function request(method, path, body = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const req = http.request(
      {
        hostname: "127.0.0.1",
        port: PORT,
        path,
        method,
        headers: {
          "Content-Type": "application/json",
          ...(data ? { "Content-Length": Buffer.byteLength(data) } : {}),
          ...headers,
        },
      },
      (res) => {
        let resData = "";
        res.on("data", (chunk) => (resData += chunk));
        res.on("end", () => {
          let parsed;
          try {
            parsed = JSON.parse(resData);
          } catch (e) {
            parsed = resData;
          }
          resolve({ status: res.statusCode, body: parsed });
        });
      }
    );
    req.on("error", reject);
    if (data) req.write(data);
    req.end();
  });
}

function assert(condition, message) {
  if (!condition) {
    console.error(`❌ FAILED: ${message}`);
    process.exit(1);
  }
  console.log(`✅ PASSED: ${message}`);
}

async function runTests() {
  server = app.listen(PORT, "127.0.0.1");
  console.log(`Running Backend Auth Test Suite on port ${PORT}...`);

  try {
    // 1. Health check
    const health = await request("GET", "/health");
    assert(health.status === 200 && health.body.status === "ok", "Health endpoint responds 200 OK");

    // 2. Reject invalid email
    const badEmail = await request("POST", "/auth/send-otp", { email: "notanemail" });
    assert(badEmail.status === 400 && badEmail.body.success === false, "Rejects malformed email address");

    // 3. Send OTP
    const testEmail = "testuser@loanwise.bank";
    const sendRes = await request("POST", "/auth/send-otp", { email: testEmail });
    assert(sendRes.status === 200 && sendRes.body.success === true, "Send OTP succeeds for valid email");
    const validOtp = sendRes.body._testOtp;
    assert(validOtp && validOtp.length === 6, "Generated OTP is exactly 6 digits");

    // 4. Rate limiting / Cooldown check
    const fastResend = await request("POST", "/auth/send-otp", { email: testEmail });
    assert(
      fastResend.status === 429 && fastResend.body.message.includes("Please wait before requesting another OTP"),
      "Enforces cooldown rate limit when requesting OTP too quickly"
    );

    // 5. Reject empty or invalid OTP format
    const emptyOtp = await request("POST", "/auth/verify-otp", { email: testEmail, otp: "123" });
    assert(emptyOtp.status === 400 && emptyOtp.body.message === "Enter the OTP", "Rejects OTP with incorrect length");

    // 6. Reject wrong OTP code
    const wrongOtp = await request("POST", "/auth/verify-otp", { email: testEmail, otp: "000000" });
    assert(wrongOtp.status === 400 && wrongOtp.body.message === "Invalid OTP", "Rejects incorrect OTP code");

    // 7. Successful OTP verification with JWT generation
    const verifySuccess = await request("POST", "/auth/verify-otp", { email: testEmail, otp: validOtp });
    assert(
      verifySuccess.status === 200 &&
      verifySuccess.body.success === true &&
      verifySuccess.body.message === "Login successful" &&
      !!verifySuccess.body.token,
      "Valid OTP verification succeeds and returns JWT token"
    );

    const jwtToken = verifySuccess.body.token;

    // 8. Reject reuse of already verified OTP
    const reuseAttempt = await request("POST", "/auth/verify-otp", { email: testEmail, otp: validOtp });
    assert(
      reuseAttempt.status === 400 && reuseAttempt.body.message.includes("already been used"),
      "Rejects replay attack / reuse of already used OTP"
    );

    // 9. Verify JWT Session via /auth/me
    const meRes = await request("GET", "/auth/me", null, { Authorization: `Bearer ${jwtToken}` });
    assert(meRes.status === 200 && meRes.body.user.email === testEmail, "JWT session token verifies successfully");

    // 10. Max attempts limit test
    await new Promise((r) => setTimeout(r, 2100)); // wait for cooldown
    const email2 = "bruteforce@loanwise.bank";
    const send2 = await request("POST", "/auth/send-otp", { email: email2 });
    assert(send2.status === 200, "Second test email receives OTP");

    for (let i = 0; i < 5; i++) {
      await request("POST", "/auth/verify-otp", { email: email2, otp: "999999" });
    }
    const lockedOut = await request("POST", "/auth/verify-otp", { email: email2, otp: send2.body._testOtp });
    assert(
      lockedOut.status === 429 && lockedOut.body.message.includes("Too many attempts"),
      "Locks out and rejects after 5 failed attempts even with correct OTP"
    );

    console.log("\n🎉 ALL 10 BACKEND VERIFICATION TESTS PASSED SUCCESSFULLY!\n");
  } finally {
    server.close();
  }
}

runTests();

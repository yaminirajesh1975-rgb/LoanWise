require("dotenv").config();
const express = require("express");
const cors = require("cors");
const crypto = require("crypto");
const jwt = require("jsonwebtoken");
const { Resend } = require("resend");
const { Redis } = require("@upstash/redis");

const app = express();
app.use(cors());
app.use(express.json());

// Configuration
const PORT = process.env.PORT || 5001;
const JWT_SECRET = process.env.JWT_SECRET || "loanwise_default_super_secret_jwt_key_2026";
const RESEND_API_KEY = process.env.RESEND_API_KEY || "";
const EMAIL_FROM = process.env.EMAIL_FROM || "LoanWise <onboarding@resend.dev>";
const OTP_EXPIRY_MS = (parseInt(process.env.OTP_EXPIRY_MINUTES, 10) || 5) * 60 * 1000;
const MAX_ATTEMPTS = parseInt(process.env.MAX_OTP_ATTEMPTS, 10) || 5;
const COOLDOWN_MS = (parseInt(process.env.RESEND_COOLDOWN_SECONDS, 10) || 60) * 1000;

// Deterministic server salt for OTP hashing across serverless invocations
const HASH_SALT = process.env.HASH_SALT || "loanwise_auth_salt_vercel_secure_2026";

// Initialize Resend client if key is provided
let resendClient = null;
if (RESEND_API_KEY && RESEND_API_KEY !== "your_resend_api_key_here") {
  resendClient = new Resend(RESEND_API_KEY);
}

// Initialize Vercel KV / Upstash Redis client if environment variables exist
const kvUrl = process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL;
const kvToken = process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN;

let redisClient = null;
if (kvUrl && kvToken) {
  try {
    redisClient = new Redis({
      url: kvUrl,
      token: kvToken
    });
    console.log("[Storage] Connected to Vercel KV / Upstash Redis persistent store.");
  } catch (err) {
    console.error("[Storage Error] Failed to initialize Redis client:", err.message);
  }
} else {
  console.log("[Storage] No KV credentials found. Using local in-memory store.");
}

// In-memory fallback map for local development and test suite
const memoryStore = new Map();

async function getOtpRecord(email) {
  const key = `loanwise:otp:${email}`;
  if (redisClient) {
    try {
      const data = await redisClient.get(key);
      if (!data) return null;
      if (typeof data === "object") return data;
      if (typeof data === "string") {
        try {
          return JSON.parse(data);
        } catch {
          return null;
        }
      }
      return null;
    } catch (err) {
      console.error("[Storage Error] Redis get failed, checking memory fallback:", err.message);
      return memoryStore.get(email) || null;
    }
  }
  return memoryStore.get(email) || null;
}

async function setOtpRecord(email, record, ttlSeconds = 600) {
  const key = `loanwise:otp:${email}`;
  memoryStore.set(email, record);

  if (redisClient) {
    try {
      await redisClient.set(key, record, { ex: ttlSeconds });
    } catch (err) {
      console.error("[Storage Error] Redis set failed:", err.message);
    }
  }
}

async function deleteOtpRecord(email) {
  const key = `loanwise:otp:${email}`;
  memoryStore.delete(email);

  if (redisClient) {
    try {
      await redisClient.del(key);
    } catch (err) {
      console.error("[Storage Error] Redis del failed:", err.message);
    }
  }
}

// Helper: Normalize email
function normalizeEmail(email) {
  if (!email || typeof email !== "string") return "";
  return email.trim().toLowerCase();
}

// Helper: Validate email format
function isValidEmail(email) {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
}

// Helper: Hash OTP with email and server salt
function hashOtp(email, otp) {
  return crypto
    .createHash("sha256")
    .update(`${email}:${otp}:${HASH_SALT}`)
    .digest("hex");
}

// Root Info Endpoint
app.get("/", (req, res) => {
  res.json({
    status: "ok",
    service: "LoanWise Auth API",
    storage: redisClient ? "vercel_kv" : "memory_fallback",
    healthCheck: "/health",
    resendConfigured: !!resendClient,
    timestamp: new Date().toISOString()
  });
});

// Health Check Endpoint
app.get("/health", (req, res) => {
  res.json({
    status: "ok",
    service: "LoanWise Auth API",
    storage: redisClient ? "vercel_kv" : "memory_fallback",
    resendConfigured: !!resendClient,
    timestamp: new Date().toISOString()
  });
});

// Endpoint: Send OTP
// POST /auth/send-otp
// Body: { "email": "user@example.com" }
app.post("/auth/send-otp", async (req, res) => {
  try {
    const rawEmail = req.body && req.body.email;
    const email = normalizeEmail(rawEmail);

    if (!email || !isValidEmail(email)) {
      return res.status(400).json({
        success: false,
        message: "Please enter a valid email address"
      });
    }

    const now = Date.now();
    const existing = await getOtpRecord(email);

    // Cooldown verification (Rate Limit)
    if (existing && existing.lastRequestedAt && now - existing.lastRequestedAt < COOLDOWN_MS) {
      const remainingSeconds = Math.ceil((COOLDOWN_MS - (now - existing.lastRequestedAt)) / 1000);
      return res.status(429).json({
        success: false,
        message: "Please wait before requesting another OTP",
        cooldownRemaining: remainingSeconds
      });
    }

    // Generate cryptographically secure 6-digit OTP
    const otpInt = crypto.randomInt(100000, 1000000);
    const otp = otpInt.toString();

    // Hash the OTP before storing
    const otpHash = hashOtp(email, otp);

    // Store in persistent storage (TTL = 10 minutes to cover expiry and cooldown window)
    const record = {
      otpHash,
      expiresAt: now + OTP_EXPIRY_MS,
      attempts: 0,
      used: false,
      lastRequestedAt: now
    };
    await setOtpRecord(email, record, 600);

    console.log(`[INFO] OTP generated for email: ${email.replace(/(?<=.{2}).(?=[^@]*?@)/g, "*")}`);

    // Send email via Resend
    let emailSent = false;
    let emailError = null;

    if (resendClient) {
      try {
        const { data, error } = await resendClient.emails.send({
          from: process.env.EMAIL_FROM || "LoanWise <onboarding@resend.dev>",
          to: [email],
          subject: "LoanWise Login OTP",
          text: `Your LoanWise verification code is: ${otp}\n\nThis OTP is valid for 5 minutes.\nDo not share this code with anyone.`,
          html: `<div style="font-family: Arial, sans-serif; max-width: 500px; margin: 0 auto; padding: 24px; border: 1px solid #e0e0e0; border-radius: 8px;">
            <h2 style="color: #1A73E8; margin-top: 0;">LoanWise Bank Verification</h2>
            <p>Your one-time verification code is:</p>
            <div style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #202124; background: #F1F3F4; padding: 16px; text-align: center; border-radius: 6px; margin: 20px 0;">
              ${otp}
            </div>
            <p style="color: #5F6368; font-size: 14px;">This OTP is valid for <strong>5 minutes</strong>. Do not share this code with anyone.</p>
            <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;" />
            <p style="color: #9AA0A6; font-size: 12px; margin-bottom: 0;">LoanWise Security Team</p>
          </div>`
        });

        if (error) {
          console.error(`[Resend Error] Delivery failed:`, error);
          emailError = error.message;
        } else {
          emailSent = true;
          console.log(`[INFO] OTP email successfully dispatched via Resend:`, data);
        }
      } catch (err) {
        console.error(`[Resend Exception] Error sending email: ${err.message}`);
        emailError = err.message;
      }
    } else {
      console.warn("[WARN] RESEND_API_KEY is not configured. Running in simulation mode.");
    }

    // Allow test suites to inspect OTP during automated tests
    const responsePayload = {
      success: true,
      message: "OTP sent successfully"
    };

    if (process.env.NODE_ENV === "test") {
      responsePayload._testOtp = otp;
    }

    if (emailError && !process.env.NODE_ENV) {
      return res.status(502).json({
        success: false,
        message: `Email delivery failed: ${emailError}`
      });
    }

    return res.status(200).json(responsePayload);
  } catch (error) {
    console.error("[Internal Error] /auth/send-otp:", error.message);
    return res.status(500).json({
      success: false,
      message: "An internal server error occurred while sending OTP"
    });
  }
});

// Endpoint: Verify OTP
// POST /auth/verify-otp
// Body: { "email": "user@example.com", "otp": "123456" }
app.post("/auth/verify-otp", async (req, res) => {
  try {
    const rawEmail = req.body && req.body.email;
    const rawOtp = req.body && req.body.otp;

    const email = normalizeEmail(rawEmail);
    const otp = (rawOtp || "").toString().trim();

    if (!email || !isValidEmail(email)) {
      return res.status(400).json({
        success: false,
        message: "Please enter a valid email address"
      });
    }

    if (!otp || otp.length !== 6 || !/^\d{6}$/.test(otp)) {
      return res.status(400).json({
        success: false,
        message: "Enter the OTP"
      });
    }

    const record = await getOtpRecord(email);

    if (!record) {
      return res.status(400).json({
        success: false,
        message: "No OTP found. Please request a new OTP"
      });
    }

    // Check if OTP was already used
    if (record.used) {
      return res.status(400).json({
        success: false,
        message: "OTP has already been used. Please request a new OTP"
      });
    }

    // Check expiry
    if (Date.now() > record.expiresAt) {
      return res.status(400).json({
        success: false,
        message: "OTP expired"
      });
    }

    // Check attempt limits
    if (record.attempts >= MAX_ATTEMPTS) {
      return res.status(429).json({
        success: false,
        message: "Too many attempts"
      });
    }

    // Increment attempts
    record.attempts += 1;

    // Verify hash using constant-time comparison
    const incomingHash = hashOtp(email, otp);
    const expectedHash = record.otpHash;

    const match =
      incomingHash.length === expectedHash.length &&
      crypto.timingSafeEqual(Buffer.from(incomingHash), Buffer.from(expectedHash));

    if (!match) {
      const remaining = MAX_ATTEMPTS - record.attempts;
      const ttlSeconds = Math.max(60, Math.ceil((record.expiresAt - Date.now()) / 1000));
      await setOtpRecord(email, record, ttlSeconds);

      if (remaining <= 0) {
        return res.status(429).json({
          success: false,
          message: "Too many attempts"
        });
      }
      return res.status(400).json({
        success: false,
        message: "Invalid OTP",
        attemptsRemaining: remaining
      });
    }

    // Mark OTP as used immediately to prevent replay attacks across all instances
    record.used = true;
    await setOtpRecord(email, record, 300);

    // Generate JWT token
    const token = jwt.sign(
      {
        email,
        sub: email,
        iss: "loanwise-auth"
      },
      JWT_SECRET,
      { expiresIn: "7d" }
    );

    return res.status(200).json({
      success: true,
      message: "Login successful",
      token,
      user: {
        email
      }
    });
  } catch (error) {
    console.error("[Internal Error] /auth/verify-otp:", error.message);
    return res.status(500).json({
      success: false,
      message: "An internal server error occurred while verifying OTP"
    });
  }
});

// Endpoint: Verify Session Token (JWT)
// GET /auth/me (Bearer token)
app.get("/auth/me", (req, res) => {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return res.status(401).json({ success: false, message: "Missing or invalid authorization header" });
  }

  const token = authHeader.split(" ")[1];
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    return res.json({ success: true, user: { email: decoded.email } });
  } catch (err) {
    return res.status(401).json({ success: false, message: "Invalid or expired token" });
  }
});

// Start server locally when executed directly
if (require.main === module) {
  app.listen(PORT, "0.0.0.0", () => {
    console.log(`LoanWise Auth Server running on http://0.0.0.0:${PORT}`);
    console.log(`Health check: http://localhost:${PORT}/health`);
  });
}

// Export app for Vercel Serverless Function
module.exports = app;

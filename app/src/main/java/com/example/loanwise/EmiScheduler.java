package com.example.loanwise;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Adds every EMI whose debit date has arrived to the loan's history
 * and reduces the outstanding balance. Safe to call many times:
 * it runs inside a Firestore transaction and moves "nextEmiDate" forward,
 * so the same EMI is never added twice.
 */
public class EmiScheduler {

    public interface Callback {
        void onDone(int postedCount);
    }

    private static final int MAX_PER_RUN = 60;

    public static void postDueEmis(FirebaseFirestore db, String uid, Callback callback) {
        DocumentReference loanRef = db.collection("loans").document(uid);

        db.runTransaction((Transaction.Function<Integer>) transaction -> {
            DocumentSnapshot snap = transaction.get(loanRef);
            if (!snap.exists()) return 0;

            Long nextObj = snap.getLong("nextEmiDate");
            Long dayObj = snap.getLong("emiDay");
            if (nextObj == null || dayObj == null) return 0;   // loan saved before this feature

            Double outObj = snap.getDouble("outstandingPrincipal");
            Double rateObj = snap.getDouble("interestRate");
            Double emiObj = snap.getDouble("monthlyEMI");
            Long remObj = snap.getLong("remainingTenure");

            double outstanding = outObj != null ? outObj : 0;
            double rate = rateObj != null ? rateObj : 0;
            double emi = emiObj != null ? emiObj : 0;
            long remaining = remObj != null ? remObj : 0;
            int day = dayObj.intValue();
            long next = nextObj;

            if (emi <= 0 || outstanding <= 0) return 0;

            // End of today
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 23);
            c.set(Calendar.MINUTE, 59);
            c.set(Calendar.SECOND, 59);
            c.set(Calendar.MILLISECOND, 999);
            long todayEnd = c.getTimeInMillis();

            int posted = 0;
            long now = System.currentTimeMillis();

            while (next <= todayEnd && outstanding > 0 && posted < MAX_PER_RUN) {
                double interestPart = outstanding * rate / 12.0 / 100.0;
                double paid = Math.min(emi, outstanding + interestPart);
                double principalPart = Math.min(Math.max(paid - interestPart, 0), outstanding);

                outstanding = Math.max(outstanding - principalPart, 0);
                remaining = Math.max(remaining - 1, 0);
                if (outstanding < 1) {          // fully repaid
                    outstanding = 0;
                    remaining = 0;
                }

                Map<String, Object> tx = new HashMap<>();
                tx.put("type", "EMI");
                tx.put("amount", (double) Math.round(paid));
                tx.put("balanceAfter", (double) Math.round(outstanding));
                tx.put("date", next);
                tx.put("createdAt", now);
                tx.put("auto", true);

                // Fixed ID per due date -> can never be duplicated
                transaction.set(loanRef.collection("transactions").document("emi_" + next), tx);

                next = addOneMonth(next, day);
                posted++;
            }

            if (posted > 0) {
                transaction.update(loanRef,
                        "outstandingPrincipal", (double) Math.round(outstanding),
                        "remainingTenure", remaining,
                        "nextEmiDate", next,
                        "updatedAt", now);
            }
            return posted;

        }).addOnSuccessListener(count -> {
            if (callback != null) callback.onDone(count);
        }).addOnFailureListener(e -> {
            if (callback != null) callback.onDone(0);
        });
    }

    /** Same day next month (e.g. 31st becomes 28th/30th in shorter months). */
    private static long addOneMonth(long millis, int day) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MONTH, 1);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        c.set(Calendar.DAY_OF_MONTH, Math.min(day, max));
        return c.getTimeInMillis();
    }
}
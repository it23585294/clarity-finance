const { assertFails, assertSucceeds, initializeTestEnvironment } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, query, where, orderBy, updateDoc, deleteDoc, serverTimestamp, writeBatch, runTransaction } = require('firebase/firestore');
const fs = require('fs');

describe('Clarity Expense Firestore Rules', function() {
    this.timeout(30000); // 30 seconds

    let testEnv;

    before(async () => {
        testEnv = await initializeTestEnvironment({
            projectId: 'demo-clarity-test',
            firestore: {
                rules: fs.readFileSync('../firestore.rules', 'utf8'),
                host: '127.0.0.1',
                port: 8080
            },
        });
    });

    after(async () => {
        if (testEnv) {
            await testEnv.cleanup();
        }
    });

    beforeEach(async () => {
        if (testEnv) {
            await testEnv.clearFirestore();
        }
    });

    const mockValidExpense = {
        amountMinor: 1500,
        currency: 'LKR',
        categoryId: 'food_dining',
        paymentChannelId: 'cash',
        spendingType: 'discretionary',
        expenseDate: '2023-10-23',
        note: ''
    };

    it('should allow user to create their own expense with valid data', async () => {
        const aliceContext = testEnv.authenticatedContext('alice');
        const db = aliceContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertSucceeds(setDoc(ref, {
            ...mockValidExpense,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny unauthenticated access', async () => {
        const unauth = testEnv.unauthenticatedContext();
        const db = unauth.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(getDoc(ref));
    });

    it('should deny cross-user writes', async () => {
        const bobContext = testEnv.authenticatedContext('bob');
        const db = bobContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(setDoc(ref, {
            ...mockValidExpense,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny invalid amounts (negative)', async () => {
        const aliceContext = testEnv.authenticatedContext('alice');
        const db = aliceContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(setDoc(ref, {
            ...mockValidExpense,
            amountMinor: -500,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny invalid category', async () => {
        const aliceContext = testEnv.authenticatedContext('alice');
        const db = aliceContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(setDoc(ref, {
            ...mockValidExpense,
            categoryId: 'invalid_cat',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should allow an owner to read their expense, but deny another user', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const aliceContext = testEnv.authenticatedContext('alice');
        const aliceDb = aliceContext.firestore();
        await assertSucceeds(getDoc(doc(aliceDb, 'users', 'alice', 'expenses', 'e1')));

        const bobContext = testEnv.authenticatedContext('bob');
        const bobDb = bobContext.firestore();
        await assertFails(getDoc(doc(bobDb, 'users', 'alice', 'expenses', 'e1')));
    });

    it('should allow owner to update valid expense fields with a new updatedAt timestamp', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const aliceContext = testEnv.authenticatedContext('alice');
        const db = aliceContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertSucceeds(updateDoc(ref, {
            amountMinor: 2000,
            note: 'Updated note',
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny updates that attempt to change createdAt', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const aliceContext = testEnv.authenticatedContext('alice');
        const db = aliceContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(updateDoc(ref, {
            amountMinor: 2000,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should allow owner to delete their expense', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const aliceContext = testEnv.authenticatedContext('alice');
        const db = aliceContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertSucceeds(deleteDoc(ref));
    });

    it('should deny cross-user deletions', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const bobContext = testEnv.authenticatedContext('bob');
        const db = bobContext.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(deleteDoc(ref));
    });

    it('should deny unauthenticated deletions', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'expenses', 'e1'), {
                ...mockValidExpense,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const unauth = testEnv.unauthenticatedContext();
        const db = unauth.firestore();
        const ref = doc(db, 'users', 'alice', 'expenses', 'e1');

        await assertFails(deleteDoc(ref));
    });

    // INCOME RULES TESTS
    const mockValidLkrIncome = {
        sourceId: 'salary',
        receiptDate: '2023-10-23',
        currency: 'LKR',
        originalAmountStr: '128000.00',
        lkrAmountMinor: 12800000,
        conversionBasis: 'native_lkr',
        conversionRateStr: '1',
        note: ''
    };

    const mockValidUsdRateIncome = {
        sourceId: 'freelance',
        receiptDate: '2023-10-23',
        currency: 'USD',
        originalAmountStr: '41.50',
        lkrAmountMinor: 1245000,
        conversionBasis: 'confirmed_rate',
        conversionRateStr: '300',
        note: ''
    };

    const mockValidUsdCreditIncome = {
        sourceId: 'adsense',
        receiptDate: '2023-10-23',
        currency: 'USD',
        originalAmountStr: '41.50',
        lkrAmountMinor: 1245000,
        conversionBasis: 'actual_credit',
        note: 'Bank transfer'
    };

    it('should allow user to create LKR income', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertSucceeds(setDoc(ref, {
            ...mockValidLkrIncome,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should allow user to create USD income with confirmed_rate', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i2');
        await assertSucceeds(setDoc(ref, {
            ...mockValidUsdRateIncome,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should allow user to create USD income with actual_credit (no rate)', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i3');
        await assertSucceeds(setDoc(ref, {
            ...mockValidUsdCreditIncome,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny cross-user income creation', async () => {
        const bob = testEnv.authenticatedContext('bob');
        const db = bob.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny unauthenticated income creation', async () => {
        const unauth = testEnv.unauthenticatedContext();
        const db = unauth.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should allow owner to read, update, delete income', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'incomeReceipts', 'i1'), {
                ...mockValidLkrIncome,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');

        await assertSucceeds(getDoc(ref));
        await assertSucceeds(updateDoc(ref, {
            originalAmountStr: '130000',
            lkrAmountMinor: 13000000,
            updatedAt: serverTimestamp()
        }));
        await assertSucceeds(deleteDoc(ref));
    });

    it('should deny income updates that change createdAt', async () => {
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await setDoc(doc(db, 'users', 'alice', 'incomeReceipts', 'i1'), {
                ...mockValidLkrIncome,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');

        await assertFails(updateDoc(ref, {
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny income creation with unexpected fields', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            maliciousField: true,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny income creation with zero original amount', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            originalAmountStr: '0.00',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));

        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            originalAmountStr: '000',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny income creation with oversized string', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            originalAmountStr: '123456789012345678901', // 21 chars
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny confirmed_rate income without conversionRateStr', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');
        const badUsd = { ...mockValidUsdRateIncome };
        delete badUsd.conversionRateStr;

        await assertFails(setDoc(ref, {
            ...badUsd,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny confirmed_rate income with zero or null rate', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');

        await assertFails(setDoc(ref, {
            ...mockValidUsdRateIncome,
            conversionRateStr: '0.000',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));

        await assertFails(setDoc(ref, {
            ...mockValidUsdRateIncome,
            conversionRateStr: null,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny incompatible conversion basis (LKR with actual_credit)', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'i1');

        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            conversionBasis: 'actual_credit',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });


// PENDING PAYMENTS ATOMIC RULES TESTS
    const mockValidPayment = {
        type: 'project',
        title: 'Website Redesign',
        sourceId: 'freelance',
        currency: 'USD',
        sourceId: 'freelance',
        currency: 'USD',
        dueDate: '2023-11-01',
        agreedAmountMinor: 100000, // 1,000 USD
        allocatedAmountMinor: 0,
        directReceivedAmountMinor: 0,
        milestoneReceivedAmountMinor: 0,
        isCancelled: false,
        note: ''
    };

    const mockValidUsdIncomeForPayment = {
        sourceId: 'freelance',
        receiptDate: '2023-10-23',
        currency: 'USD',
        originalAmountStr: '150.00',
        originalAmountMinor: 5000,
        lkrAmountMinor: 4500000,
        conversionBasis: 'confirmed_rate',
        conversionRateStr: '300',
        note: ''
    };

    it('should allow user to create a valid ExpectedPayment', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        await assertSucceeds(setDoc(ref, {
            ...mockValidPayment,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny creating payment with nonzero initial counters', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        await assertFails(setDoc(ref, {
            ...mockValidPayment,
            directReceivedAmountMinor: 500, // ILLEGAL
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny unauthenticated payment operations', async () => {
        const unauth = testEnv.unauthenticatedContext();
        const db = unauth.firestore();
        const ref = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        await assertFails(setDoc(ref, {
            ...mockValidPayment,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should successfully execute a valid direct payment transaction', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const rRef = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            directReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });

        batch.set(rRef, {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertSucceeds(batch.commit());
    });

    it('should successfully execute a valid partial milestone payment', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: false,
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const rRef = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            milestoneReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });

        batch.update(mRef, {
            receivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });

        batch.set(rRef, {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            linkedMilestoneId: 'm1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertSucceeds(batch.commit());
    });

    it('should deny payment transaction if parent increment is missing or wrong amount', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const rRef = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            directReceivedAmountMinor: 4000, // MISMATCH 4000 vs 5000
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });

        batch.set(rRef, {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should deny parent balance update without a new receipt', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        await assertFails(updateDoc(pRef, {
            directReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny two receipts sharing one balance increment', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const rRef1 = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');
        const rRef2 = doc(db, 'users', 'alice', 'incomeReceipts', 'r2');
        const batch = writeBatch(db);

        batch.update(pRef, {
            directReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });

        batch.set(rRef1, {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        batch.set(rRef2, {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should deny reusing an existing receipt (parent-only replay)', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'incomeReceipts', 'r1'), {
                ...mockValidUsdIncomeForPayment,
                originalAmountMinor: 5000,
                linkedPaymentId: 'p1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        // Parent updates balance and points to an already existing receipt WITHOUT overwriting it
        await assertFails(updateDoc(pRef, {
            directReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny overpayment', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                agreedAmountMinor: 10000, // 10k
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const rRef = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            directReceivedAmountMinor: 15000, // 15k > 10k
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });

        batch.set(rRef, {
            ...mockValidUsdIncomeForPayment,
            originalAmountStr: '150.00',
            originalAmountMinor: 15000,
            lkrAmountMinor: 4500000,
            linkedPaymentId: 'p1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should deny milestone allocation overflow', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                agreedAmountMinor: 100000,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            allocatedAmountMinor: 150000, // OVERFLOW
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'op1',
            updatedAt: serverTimestamp()
        });

        batch.set(mRef, {
            title: 'M1',
            dueDate: '2023-11-01',
            agreedAmountMinor: 150000,
            receivedAmountMinor: 0,
            isCancelled: false,
            lastMilestoneOpId: 'op1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should deny mismatched operation references on milestone creation', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                agreedAmountMinor: 100000,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            allocatedAmountMinor: 40000,
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'op1',
            updatedAt: serverTimestamp()
        });

        batch.set(mRef, {
            title: 'M1',
            dueDate: '2023-11-01',
            agreedAmountMinor: 40000,
            receivedAmountMinor: 0,
            isCancelled: false,
            lastMilestoneOpId: 'op2', // MISMATCH
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should allow consecutive edits to the same milestone via unique opIds', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                lastMilestoneId: 'm1',
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: false,
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');

        // EDIT 1
        let batch = writeBatch(db);
        batch.update(pRef, {
            allocatedAmountMinor: 60000,
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'op2',
            updatedAt: serverTimestamp()
        });
        batch.update(mRef, {
            agreedAmountMinor: 60000,
            lastMilestoneOpId: 'op2',
            updatedAt: serverTimestamp()
        });
        await assertSucceeds(batch.commit());

        // EDIT 2
        let batch2 = writeBatch(db);
        batch2.update(pRef, {
            allocatedAmountMinor: 70000,
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'op3',
            updatedAt: serverTimestamp()
        });
        batch2.update(mRef, {
            agreedAmountMinor: 70000,
            lastMilestoneOpId: 'op3',
            updatedAt: serverTimestamp()
        });
        await assertSucceeds(batch2.commit());
    });

    it('should deny multiple milestones sharing one allocation delta', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                agreedAmountMinor: 100000,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef1 = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        const mRef2 = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm2');
        const batch = writeBatch(db);

        batch.update(pRef, {
            allocatedAmountMinor: 40000,
            lastMilestoneId: 'm1', // Will only match m1!
            lastMilestoneOpId: 'op1',
            updatedAt: serverTimestamp()
        });

        batch.set(mRef1, {
            title: 'M1',
            dueDate: '2023-11-01',
            agreedAmountMinor: 40000,
            receivedAmountMinor: 0,
            isCancelled: false,
            lastMilestoneOpId: 'op1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        batch.set(mRef2, {
            title: 'M2',
            dueDate: '2023-11-01',
            agreedAmountMinor: 40000,
            receivedAmountMinor: 0,
            isCancelled: false,
            lastMilestoneOpId: 'op1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should allow cancellation preserving history and releasing only unpaid allocation', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                milestoneReceivedAmountMinor: 15000,
                lastMilestoneId: 'm1',
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 15000,
                isCancelled: false,
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'incomeReceipts', 'r1'), {
                ...mockValidUsdIncomeForPayment,
                originalAmountStr: '150.00',
                originalAmountMinor: 15000,
                lkrAmountMinor: 4500000,
                linkedPaymentId: 'p1',
                linkedMilestoneId: 'm1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        const batch = writeBatch(db);

        // Cancel milestone: agreed stays 40k, received stays 15k.
        // It releases 25k (40k - 15k) unpaid allocation back to the parent.
        batch.update(pRef, {
            allocatedAmountMinor: 15000, // 40000 - 25000
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'opCancel',
            updatedAt: serverTimestamp()
        });

        batch.update(mRef, {
            isCancelled: true,
            lastMilestoneOpId: 'opCancel',
            updatedAt: serverTimestamp()
        });

        await assertSucceeds(batch.commit());

        // Assert snapshot values
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            const mSnap = await getDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'));
            if(mSnap.data().agreedAmountMinor !== 40000) throw new Error("Agreed amount mutated");
            if(mSnap.data().receivedAmountMinor !== 15000) throw new Error("Received amount mutated");
            if(mSnap.data().isCancelled !== true) throw new Error("Milestone not cancelled");

            const pSnap = await getDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'));
            if(pSnap.data().allocatedAmountMinor !== 15000) throw new Error("Parent allocation not updated");
            if(pSnap.data().milestoneReceivedAmountMinor !== 15000) throw new Error("Parent milestoneReceived mutated");
            if(pSnap.data().agreedAmountMinor !== 100000) throw new Error("Parent agreed mutated");
            if(pSnap.data().directReceivedAmountMinor !== 0) throw new Error("Parent direct received mutated");

            const rSnap = await getDoc(doc(adminDb, 'users', 'alice', 'incomeReceipts', 'r1'));
            if(!rSnap.exists()) throw new Error("Receipt missing");
            if(rSnap.data().originalAmountMinor !== 15000) throw new Error("Receipt data mutated");
        });
    });

    it('should deny linked receipt edits, deletion, and link removal', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'incomeReceipts', 'r1'), {
                ...mockValidUsdIncomeForPayment,
                originalAmountMinor: 5000,
                linkedPaymentId: 'p1', // Linked!
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const rRef = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');

        await assertFails(deleteDoc(rRef));

        await assertFails(updateDoc(rRef, {
            note: 'Malicious edit',
            updatedAt: serverTimestamp()
        }));

        await assertFails(updateDoc(rRef, {
            linkedPaymentId: null,
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny cross-user payment operations', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const bob = testEnv.authenticatedContext('bob');

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(bob.firestore(), 'users', 'alice', 'expectedPayments', 'p1');
        await assertFails(updateDoc(pRef, {
            title: 'Hacked',
            updatedAt: serverTimestamp()
        }));
    });


    it('should deny unchanged/missing operation-ID bypasses for milestone payment', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                lastReceiptId: 'old_receipt',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: false,
                lastMilestoneOpId: 'op1',
                lastReceiptId: 'old_receipt',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            milestoneReceivedAmountMinor: 5000,
            updatedAt: serverTimestamp()
        });

        batch.update(mRef, {
            receivedAmountMinor: 5000,
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should deny milestone-only received updates without parent update', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: false,
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        const rRef = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');
        const batch = writeBatch(db);

        batch.update(mRef, {
            receivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });

        batch.set(rRef, {
            ...mockValidUsdIncomeForPayment,
            originalAmountStr: '150.00',
            originalAmountMinor: 15000,
            lkrAmountMinor: 4500000,
            linkedPaymentId: 'p1',
            linkedMilestoneId: 'm1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should deny incorrect allocation deltas despite matching operation IDs', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                agreedAmountMinor: 100000,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            allocatedAmountMinor: 50000,
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'op1',
            updatedAt: serverTimestamp()
        });

        batch.set(mRef, {
            title: 'M1',
            dueDate: '2023-11-01',
            agreedAmountMinor: 40000,
            receivedAmountMinor: 0,
            isCancelled: false,
            lastMilestoneOpId: 'op1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should deny repeated cancellation from releasing allocation again', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 15000,
                milestoneReceivedAmountMinor: 15000,
                lastMilestoneId: 'm1',
                lastMilestoneOpId: 'opCancel',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 15000,
                isCancelled: true,
                lastMilestoneOpId: 'opCancel',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            allocatedAmountMinor: -10000,
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'opCancel2',
            updatedAt: serverTimestamp()
        });

        batch.update(mRef, {
            isCancelled: true,
            lastMilestoneOpId: 'opCancel2',
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should deny income creation with lkrAmountMinor = 0', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();
        const ref = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');

        await assertFails(setDoc(ref, {
            ...mockValidLkrIncome,
            lkrAmountMinor: 0,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny milestone-only agreement edits or cancellation reusing existing operation IDs without updating parent allocation', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                type: 'project',
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                lastMilestoneId: 'm1',
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: false,
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');

        await assertFails(updateDoc(mRef, {
            agreedAmountMinor: 50000,
            lastMilestoneOpId: 'op1',
            updatedAt: serverTimestamp()
        }));

        await assertFails(updateDoc(mRef, {
            isCancelled: true,
            lastMilestoneOpId: 'op1',
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny parent milestoneReceivedAmountMinor increments without the corresponding milestone update', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                type: 'project',
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: false,
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const rRef = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            milestoneReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });

        batch.set(rRef, {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            linkedMilestoneId: 'm1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should deny milestone payments incorrectly incrementing the direct-payment counter', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                type: 'project',
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: false,
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        const rRef = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            directReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });

        batch.update(mRef, {
            receivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });

        batch.set(rRef, {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            linkedMilestoneId: 'm1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should deny payments against cancelled parents or cancelled milestones', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                type: 'project',
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                isCancelled: true,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: true,
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        const rRef = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');

        const batch1 = writeBatch(db);
        batch1.update(pRef, {
            directReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });
        batch1.set(rRef, {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });
        await assertFails(batch1.commit());

        const batch2 = writeBatch(db);
        batch2.update(pRef, {
            milestoneReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });
        batch2.update(mRef, {
            receivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });
        batch2.set(rRef, {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            linkedMilestoneId: 'm1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });
        await assertFails(batch2.commit());
    });

    it('should deny receipt currency/source mismatches with the parent', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                currency: 'USD',
                sourceId: 'freelance',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const rRef = doc(db, 'users', 'alice', 'incomeReceipts', 'r1');

        const batch1 = writeBatch(db);
        batch1.update(pRef, {
            directReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });
        batch1.set(rRef, {
            ...mockValidUsdIncomeForPayment,
            currency: 'LKR',
            sourceId: 'freelance',
            conversionBasis: 'native_lkr',
            conversionRateStr: '1',
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });
        await assertFails(batch1.commit());

        const batch2 = writeBatch(db);
        batch2.update(pRef, {
            directReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });
        batch2.set(rRef, {
            ...mockValidUsdIncomeForPayment,
            currency: 'USD',
            sourceId: 'salary',
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });
        await assertFails(batch2.commit());
    });

    it('should deny negative, fractional, or out-of-range balance counters', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');

        await assertFails(setDoc(pRef, {
            ...mockValidPayment,
            agreedAmountMinor: -1000,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));

        await assertFails(setDoc(pRef, {
            ...mockValidPayment,
            agreedAmountMinor: 100.5,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));
    });

    it('should deny milestones created under standalone payments', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                type: 'standalone',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');
        const batch = writeBatch(db);

        batch.update(pRef, {
            allocatedAmountMinor: 10000,
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'op1',
            updatedAt: serverTimestamp()
        });

        batch.set(mRef, {
            title: 'M1',
            dueDate: '2023-11-01',
            agreedAmountMinor: 10000,
            receivedAmountMinor: 0,
            isCancelled: false,
            lastMilestoneOpId: 'op1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });

        await assertFails(batch.commit());
    });

    it('should handle active parent with cancelled milestone and cancelled parent with active milestone', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                type: 'project',
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 0,
                isCancelled: false,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: true,
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });

            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p2'), {
                ...mockValidPayment,
                type: 'project',
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                isCancelled: true,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p2', 'milestones', 'm2'), {
                title: 'M2',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: false,
                lastMilestoneOpId: 'op2',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const batch1 = writeBatch(db);
        batch1.update(doc(db, 'users', 'alice', 'expectedPayments', 'p1'), {
            directReceivedAmountMinor: 5000,
            lastReceiptId: 'r1',
            updatedAt: serverTimestamp()
        });
        batch1.set(doc(db, 'users', 'alice', 'incomeReceipts', 'r1'), {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });
        await assertSucceeds(batch1.commit());

        const batch2 = writeBatch(db);
        batch2.update(doc(db, 'users', 'alice', 'expectedPayments', 'p1'), {
            milestoneReceivedAmountMinor: 5000,
            lastReceiptId: 'r2',
            updatedAt: serverTimestamp()
        });
        batch2.update(doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
            receivedAmountMinor: 5000,
            lastReceiptId: 'r2',
            updatedAt: serverTimestamp()
        });
        batch2.set(doc(db, 'users', 'alice', 'incomeReceipts', 'r2'), {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p1',
            linkedMilestoneId: 'm1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });
        await assertFails(batch2.commit());

        const batch3 = writeBatch(db);
        batch3.update(doc(db, 'users', 'alice', 'expectedPayments', 'p2'), {
            milestoneReceivedAmountMinor: 5000,
            lastReceiptId: 'r3',
            updatedAt: serverTimestamp()
        });
        batch3.update(doc(db, 'users', 'alice', 'expectedPayments', 'p2', 'milestones', 'm2'), {
            receivedAmountMinor: 5000,
            lastReceiptId: 'r3',
            updatedAt: serverTimestamp()
        });
        batch3.set(doc(db, 'users', 'alice', 'incomeReceipts', 'r3'), {
            ...mockValidUsdIncomeForPayment,
            originalAmountMinor: 5000,
            linkedPaymentId: 'p2',
            linkedMilestoneId: 'm2',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });
        await assertFails(batch3.commit());
    });

    it('should deny updates with fractional, zero, or above-maximum amounts', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                type: 'project',
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                lastMilestoneId: 'm1',
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: false,
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');

        await assertFails(updateDoc(pRef, {
            agreedAmountMinor: 100.5,
            updatedAt: serverTimestamp()
        }));

        await assertFails(updateDoc(pRef, {
            agreedAmountMinor: 0,
            updatedAt: serverTimestamp()
        }));

        await assertFails(updateDoc(pRef, {
            agreedAmountMinor: 100000000001,
            updatedAt: serverTimestamp()
        }));

        // Fractional milestone & allocation update (40000.5) -> FAIL
        const fractionalBatch = writeBatch(db);
        fractionalBatch.update(pRef, {
            allocatedAmountMinor: 40000.5,
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'op2',
            updatedAt: serverTimestamp()
        });
        fractionalBatch.update(mRef, {
            agreedAmountMinor: 40000.5,
            lastMilestoneOpId: 'op2',
            updatedAt: serverTimestamp()
        });
        await assertFails(fractionalBatch.commit());

        // Valid integer counterpart (50000) -> SUCCEED
        const validBatch = writeBatch(db);
        validBatch.update(pRef, {
            allocatedAmountMinor: 50000,
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'op3',
            updatedAt: serverTimestamp()
        });
        validBatch.update(mRef, {
            agreedAmountMinor: 50000,
            lastMilestoneOpId: 'op3',
            updatedAt: serverTimestamp()
        });
        await assertSucceeds(validBatch.commit());
    });

    it('should enforce updatedAt == request.time and reject arbitrary timestamps on parent and milestone updates', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1'), {
                ...mockValidPayment,
                type: 'project',
                agreedAmountMinor: 100000,
                allocatedAmountMinor: 40000,
                lastMilestoneId: 'm1',
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1'), {
                title: 'M1',
                dueDate: '2023-11-01',
                agreedAmountMinor: 40000,
                receivedAmountMinor: 0,
                isCancelled: false,
                lastMilestoneOpId: 'op1',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1');
        const mRef = doc(db, 'users', 'alice', 'expectedPayments', 'p1', 'milestones', 'm1');

        await assertFails(updateDoc(pRef, {
            title: 'Updated Title',
            updatedAt: '2023-10-25T00:00:00Z'
        }));

        // Otherwise-valid atomic edit but milestone has old Timestamp -> FAIL
        const oldTimestampBatch = writeBatch(db);
        oldTimestampBatch.update(pRef, {
            allocatedAmountMinor: 50000,
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'op2',
            updatedAt: serverTimestamp()
        });
        oldTimestampBatch.update(mRef, {
            agreedAmountMinor: 50000,
            lastMilestoneOpId: 'op2',
            updatedAt: new Date(2020, 0, 1)
        });
        await assertFails(oldTimestampBatch.commit());

        // Otherwise-valid atomic edit with serverTimestamp() -> SUCCEED
        const serverTimestampBatch = writeBatch(db);
        serverTimestampBatch.update(pRef, {
            allocatedAmountMinor: 50000,
            lastMilestoneId: 'm1',
            lastMilestoneOpId: 'op3',
            updatedAt: serverTimestamp()
        });
        serverTimestampBatch.update(mRef, {
            agreedAmountMinor: 50000,
            lastMilestoneOpId: 'op3',
            updatedAt: serverTimestamp()
        });
        await assertSucceeds(serverTimestampBatch.commit());
    });

    it('should allow creation/update at agreed maximum (100000000000) and deny immediately above it (100000000001)', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        const p1Ref = doc(db, 'users', 'alice', 'expectedPayments', 'pMax');
        await assertSucceeds(setDoc(p1Ref, {
            ...mockValidPayment,
            agreedAmountMinor: 100000000000,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));

        const p2Ref = doc(db, 'users', 'alice', 'expectedPayments', 'pAboveMax');
        await assertFails(setDoc(p2Ref, {
            ...mockValidPayment,
            agreedAmountMinor: 100000000001,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        }));

        await assertSucceeds(updateDoc(p1Ref, {
            title: 'Updated Max',
            agreedAmountMinor: 100000000000,
            updatedAt: serverTimestamp()
        }));

        await assertFails(updateDoc(p1Ref, {
            agreedAmountMinor: 100000000001,
            updatedAt: serverTimestamp()
        }));
    });

    it('should allow milestone creation and update at agreed maximum (100000000000) and deny immediately above it (100000000001)', async () => {
        const alice = testEnv.authenticatedContext('alice');
        const db = alice.firestore();

        await testEnv.withSecurityRulesDisabled(async (context) => {
            const adminDb = context.firestore();
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'pProjectMax'), {
                ...mockValidPayment,
                type: 'project',
                agreedAmountMinor: 100000000000,
                allocatedAmountMinor: 0,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
            await setDoc(doc(adminDb, 'users', 'alice', 'expectedPayments', 'pProjectAboveMax'), {
                ...mockValidPayment,
                type: 'project',
                agreedAmountMinor: 100000000001,
                allocatedAmountMinor: 0,
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });
        });

        const pMaxRef = doc(db, 'users', 'alice', 'expectedPayments', 'pProjectMax');
        const mMaxRef = doc(db, 'users', 'alice', 'expectedPayments', 'pProjectMax', 'milestones', 'mMax');

        const createMaxBatch = writeBatch(db);
        createMaxBatch.update(pMaxRef, {
            allocatedAmountMinor: 100000000000,
            lastMilestoneId: 'mMax',
            lastMilestoneOpId: 'opMax1',
            updatedAt: serverTimestamp()
        });
        createMaxBatch.set(mMaxRef, {
            title: 'Max Milestone',
            dueDate: '2023-11-01',
            agreedAmountMinor: 100000000000,
            receivedAmountMinor: 0,
            isCancelled: false,
            lastMilestoneOpId: 'opMax1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });
        await assertSucceeds(createMaxBatch.commit());

        const pAboveMaxRef = doc(db, 'users', 'alice', 'expectedPayments', 'pProjectAboveMax');
        const mAboveMaxRef = doc(db, 'users', 'alice', 'expectedPayments', 'pProjectAboveMax', 'milestones', 'mAboveMax');
        const createAboveMaxBatch = writeBatch(db);
        createAboveMaxBatch.update(pAboveMaxRef, {
            allocatedAmountMinor: 100000000001,
            lastMilestoneId: 'mAboveMax',
            lastMilestoneOpId: 'opAboveMax1',
            updatedAt: serverTimestamp()
        });
        createAboveMaxBatch.set(mAboveMaxRef, {
            title: 'Above Max Milestone',
            dueDate: '2023-11-01',
            agreedAmountMinor: 100000000001,
            receivedAmountMinor: 0,
            isCancelled: false,
            lastMilestoneOpId: 'opAboveMax1',
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp()
        });
        await assertFails(createAboveMaxBatch.commit());

        const updateMaxBatch = writeBatch(db);
        updateMaxBatch.update(pMaxRef, {
            allocatedAmountMinor: 100000000000,
            lastMilestoneId: 'mMax',
            lastMilestoneOpId: 'opMax2',
            updatedAt: serverTimestamp()
        });
        updateMaxBatch.update(mMaxRef, {
            agreedAmountMinor: 100000000000,
            lastMilestoneOpId: 'opMax2',
            updatedAt: serverTimestamp()
        });
        await assertSucceeds(updateMaxBatch.commit());

        const updateAboveMaxBatch = writeBatch(db);
        updateAboveMaxBatch.update(pMaxRef, {
            allocatedAmountMinor: 100000000001,
            lastMilestoneId: 'mMax',
            lastMilestoneOpId: 'opMax3',
            updatedAt: serverTimestamp()
        });
        updateAboveMaxBatch.update(mMaxRef, {
            agreedAmountMinor: 100000000001,
            lastMilestoneOpId: 'opMax3',
            updatedAt: serverTimestamp()
        });
        await assertFails(updateAboveMaxBatch.commit());
    });

    describe('publication regressions', () => {
        const ownerDb = () => testEnv.authenticatedContext('alice').firestore();
        const parentRef = (db) => doc(db, 'users/alice/expectedPayments/p1');
        const milestoneRef = (db) => doc(db, 'users/alice/expectedPayments/p1/milestones/m1');
        const receiptRef = (db) => doc(db, 'users/alice/incomeReceipts/r1');
        const parentData = (extra = {}) => ({
            ...mockValidPayment, createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...extra
        });
        const milestoneData = (extra = {}) => ({
            title: 'Milestone', dueDate: '2026-10-30', agreedAmountMinor: 40000,
            receivedAmountMinor: 0, isCancelled: false, lastMilestoneOpId: 'op1',
            createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...extra
        });
        const receiptData = (extra = {}) => ({
            ...mockValidUsdIncomeForPayment, originalAmountStr: '50.00', originalAmountMinor: 5000,
            lkrAmountMinor: 1500000, linkedPaymentId: 'p1',
            createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...extra
        });
        async function seed(parent = {}, milestone = null) {
            await testEnv.withSecurityRulesDisabled(async context => {
                const db = context.firestore();
                await setDoc(parentRef(db), parentData(parent));
                if (milestone !== null) await setDoc(milestoneRef(db), milestoneData(milestone));
            });
        }

        it('rejects zero-counter parent plus unallocated child creation in one batch', async () => {
            const db = ownerDb();
            const batch = writeBatch(db);
            batch.set(parentRef(db), parentData({ lastMilestoneId: 'm1', lastMilestoneOpId: 'op1' }));
            batch.set(milestoneRef(db), milestoneData());
            await assertFails(batch.commit());
        });

        it('rejects zero-counter parent plus uncredited direct receipt creation in one batch', async () => {
            const db = ownerDb();
            const batch = writeBatch(db);
            batch.set(parentRef(db), parentData({ lastReceiptId: 'r1' }));
            batch.set(receiptRef(db), receiptData());
            await assertFails(batch.commit());
        });

        it('rejects new parent child and receipt with all received counters left zero', async () => {
            const db = ownerDb();
            const batch = writeBatch(db);
            batch.set(parentRef(db), parentData({
                lastMilestoneId: 'm1', lastMilestoneOpId: 'op1', lastReceiptId: 'r1'
            }));
            batch.set(milestoneRef(db), milestoneData({ lastReceiptId: 'r1' }));
            batch.set(receiptRef(db), receiptData({ linkedMilestoneId: 'm1' }));
            await assertFails(batch.commit());
        });

        it('rejects parent creation with seeded operation pointers even before a child write', async () => {
            const db = ownerDb();
            await assertFails(setDoc(parentRef(db), parentData({ lastReceiptId: 'r1' })));
        });

        it('rejects milestone creation that seeds a receipt pointer', async () => {
            const db = ownerDb();
            await seed();
            const batch = writeBatch(db);
            batch.update(parentRef(db), {
                allocatedAmountMinor: 40000, lastMilestoneId: 'm1', lastMilestoneOpId: 'op1',
                updatedAt: serverTimestamp()
            });
            batch.set(milestoneRef(db), milestoneData({ lastReceiptId: 'r1' }));
            await assertFails(batch.commit());
        });

        it('rejects a receipt-only write against a stale parent receipt pointer', async () => {
            const db = ownerDb();
            await seed({ lastReceiptId: 'r1' });
            await assertFails(setDoc(receiptRef(db), receiptData()));
        });

        it('rejects a parent-only milestone increment against a seeded child receipt pointer', async () => {
            const db = ownerDb();
            await seed({ allocatedAmountMinor: 40000 }, { lastReceiptId: 'r1' });
            const batch = writeBatch(db);
            batch.update(parentRef(db), {
                milestoneReceivedAmountMinor: 5000, lastReceiptId: 'r1', updatedAt: serverTimestamp()
            });
            batch.set(receiptRef(db), receiptData({ linkedMilestoneId: 'm1' }));
            await assertFails(batch.commit());
        });

        it('rejects new allocations and milestones on an already cancelled project', async () => {
            const db = ownerDb();
            await seed({ isCancelled: true });
            const batch = writeBatch(db);
            batch.update(parentRef(db), {
                allocatedAmountMinor: 40000, lastMilestoneId: 'm1', lastMilestoneOpId: 'op1',
                updatedAt: serverTimestamp()
            });
            batch.set(milestoneRef(db), milestoneData());
            await assertFails(batch.commit());
        });

        it('rejects agreement increases and decreases on milestones of a cancelled project', async () => {
            const db = ownerDb();
            await seed({ isCancelled: true, allocatedAmountMinor: 40000 }, {});
            for (const amount of [50000, 30000]) {
                const batch = writeBatch(db);
                batch.update(parentRef(db), {
                    allocatedAmountMinor: amount, lastMilestoneId: 'm1', lastMilestoneOpId: 'opEdit',
                    updatedAt: serverTimestamp()
                });
                batch.update(milestoneRef(db), {
                    agreedAmountMinor: amount, lastMilestoneOpId: 'opEdit', updatedAt: serverTimestamp()
                });
                await assertFails(batch.commit());
            }
        });

        it('rejects child creation in the same batch that cancels its parent', async () => {
            const db = ownerDb();
            await seed({ lastMilestoneId: 'm1', lastMilestoneOpId: 'op1' });
            const batch = writeBatch(db);
            batch.update(parentRef(db), { isCancelled: true, updatedAt: serverTimestamp() });
            batch.set(milestoneRef(db), milestoneData());
            await assertFails(batch.commit());
        });

        it('rejects an orphan milestone link on an otherwise standalone income', async () => {
            const db = ownerDb();
            await assertFails(setDoc(receiptRef(db), receiptData({
                linkedPaymentId: null, linkedMilestoneId: 'm1'
            })));
        });

        it('accepts the repository blank-note expense payload and preserves owner CRUD', async () => {
            const db = ownerDb();
            const ref = doc(db, 'users/alice/expenses/blank-note');
            const { note, ...withoutNote } = mockValidExpense;
            await assertSucceeds(setDoc(ref, {
                ...withoutNote, createdAt: serverTimestamp(), updatedAt: serverTimestamp()
            }));
            await assertSucceeds(updateDoc(ref, { amountMinor: 2000, updatedAt: serverTimestamp() }));
            await assertSucceeds(getDoc(ref));
            await assertSucceeds(deleteDoc(ref));
        });

        it('keeps optional expense notes typed bounded and within the existing schema', async () => {
            const db = ownerDb();
            const ref = doc(db, 'users/alice/expenses/invalid-note');
            for (const note of [null, 123, 'x'.repeat(501)]) {
                await assertFails(setDoc(ref, {
                    ...mockValidExpense, note, createdAt: serverTimestamp(), updatedAt: serverTimestamp()
                }));
            }
            await assertFails(setDoc(ref, {
                ...mockValidExpense, unexpected: true, createdAt: serverTimestamp(), updatedAt: serverTimestamp()
            }));
        });

        it('keeps LKR rate-based USD and actual-credit standalone income payloads compatible', async () => {
            const db = ownerDb();
            const variants = [
                { currency: 'LKR', conversionBasis: 'native_lkr', conversionRateStr: '1', lkrAmountMinor: 5000 },
                { currency: 'USD', conversionBasis: 'confirmed_rate', conversionRateStr: '300', lkrAmountMinor: 1500000 },
                { currency: 'USD', conversionBasis: 'actual_credit', lkrAmountMinor: 1490000 }
            ];
            for (let i = 0; i < variants.length; i++) {
                const ref = doc(db, 'users/alice/incomeReceipts/standalone-' + i);
                const { linkedPaymentId, originalAmountMinor, conversionRateStr, ...base } = receiptData();
                await assertSucceeds(setDoc(ref, { ...base, ...variants[i] }));
                await assertSucceeds(updateDoc(ref, { note: 'Edited', updatedAt: serverTimestamp() }));
                await assertSucceeds(deleteDoc(ref));
            }
        });

        it('preserves unpaid-release cancellation after parent cancellation without accepting new allocations', async () => {
            const db = ownerDb();
            for (const received of [0, 15000]) {
                await seed({
                    isCancelled: true, allocatedAmountMinor: 40000, milestoneReceivedAmountMinor: received
                }, { receivedAmountMinor: received });
                const batch = writeBatch(db);
                batch.update(parentRef(db), {
                    allocatedAmountMinor: received, lastMilestoneId: 'm1', lastMilestoneOpId: 'opCancel',
                    updatedAt: serverTimestamp()
                });
                batch.update(milestoneRef(db), {
                    isCancelled: true, lastMilestoneOpId: 'opCancel', updatedAt: serverTimestamp()
                });
                await assertSucceeds(batch.commit());
                const child = (await getDoc(milestoneRef(db))).data();
                const parent = (await getDoc(parentRef(db))).data();
                if (child.agreedAmountMinor !== 40000 || child.receivedAmountMinor !== received ||
                    parent.allocatedAmountMinor !== received || parent.milestoneReceivedAmountMinor !== received) {
                    throw new Error('Cancellation changed agreement or received history');
                }
            }
        });

        it('accepts standalone expected-payment creation followed by a direct receipt', async () => {
            const db = ownerDb();
            await assertSucceeds(setDoc(parentRef(db), parentData({ type: 'standalone' })));
            const batch = writeBatch(db);
            batch.update(parentRef(db), {
                directReceivedAmountMinor: 5000, lastReceiptId: 'r1', updatedAt: serverTimestamp()
            });
            batch.set(receiptRef(db), receiptData());
            await assertSucceeds(batch.commit());
        });

        it('preserves owner-only collection reads and rejects cross-user atomic payments', async () => {
            await seed({ allocatedAmountMinor: 40000 }, {});
            const alice = ownerDb();
            const bob = testEnv.authenticatedContext('bob').firestore();
            const anonymous = testEnv.unauthenticatedContext().firestore();
            await assertSucceeds(getDocs(collection(alice, 'users/alice/expectedPayments')));
            for (const db of [bob, anonymous]) {
                await assertFails(getDocs(collection(db, 'users/alice/expectedPayments')));
                await assertFails(getDocs(collection(db, 'users/alice/expectedPayments/p1/milestones')));
                await assertFails(getDoc(milestoneRef(db)));
                await assertFails(getDoc(receiptRef(db)));
                const batch = writeBatch(db);
                batch.update(parentRef(db), {
                    directReceivedAmountMinor: 5000, lastReceiptId: 'r1', updatedAt: serverTimestamp()
                });
                batch.set(receiptRef(db), receiptData());
                await assertFails(batch.commit());
            }
        });

        it('runs production-shaped project payment allocation and cancellation transactions end to end', async () => {
            const db = ownerDb();
            await assertSucceeds(runTransaction(db, async tx => {
                await tx.get(parentRef(db));
                tx.set(parentRef(db), parentData());
            }));
            await assertSucceeds(runTransaction(db, async tx => {
                await tx.get(receiptRef(db));
                await tx.get(parentRef(db));
                tx.update(parentRef(db), {
                    directReceivedAmountMinor: 5000, lastReceiptId: 'r1', updatedAt: serverTimestamp()
                });
                tx.set(receiptRef(db), receiptData());
            }));
            const replay = writeBatch(db);
            replay.update(parentRef(db), {
                directReceivedAmountMinor: 10000, lastReceiptId: 'r1', updatedAt: serverTimestamp()
            });
            replay.set(receiptRef(db), receiptData());
            await assertFails(replay.commit());
            await assertSucceeds(runTransaction(db, async tx => {
                await tx.get(milestoneRef(db));
                await tx.get(parentRef(db));
                tx.update(parentRef(db), {
                    allocatedAmountMinor: 40000, lastMilestoneId: 'm1', lastMilestoneOpId: 'op1',
                    updatedAt: serverTimestamp()
                });
                tx.set(milestoneRef(db), milestoneData());
            }));
            const secondReceipt = doc(db, 'users/alice/incomeReceipts/r2');
            await assertSucceeds(runTransaction(db, async tx => {
                await tx.get(secondReceipt);
                await tx.get(parentRef(db));
                await tx.get(milestoneRef(db));
                tx.update(parentRef(db), {
                    milestoneReceivedAmountMinor: 15000, lastReceiptId: 'r2', updatedAt: serverTimestamp()
                });
                tx.update(milestoneRef(db), {
                    receivedAmountMinor: 15000, lastReceiptId: 'r2', updatedAt: serverTimestamp()
                });
                tx.set(secondReceipt, receiptData({
                    originalAmountStr: '150.00', originalAmountMinor: 15000, lkrAmountMinor: 4500000,
                    linkedMilestoneId: 'm1'
                }));
            }));
            await assertSucceeds(runTransaction(db, async tx => {
                await tx.get(milestoneRef(db));
                await tx.get(parentRef(db));
                tx.update(parentRef(db), {
                    allocatedAmountMinor: 15000, lastMilestoneId: 'm1', lastMilestoneOpId: 'opCancel',
                    updatedAt: serverTimestamp()
                });
                tx.update(milestoneRef(db), {
                    isCancelled: true, lastMilestoneOpId: 'opCancel', updatedAt: serverTimestamp()
                });
            }));
            await assertSucceeds(runTransaction(db, async tx => {
                await tx.get(parentRef(db));
                tx.update(parentRef(db), { isCancelled: true, updatedAt: serverTimestamp() });
            }));
            const parent = (await getDoc(parentRef(db))).data();
            if (parent.directReceivedAmountMinor !== 5000 || parent.milestoneReceivedAmountMinor !== 15000 ||
                parent.allocatedAmountMinor !== 15000 || parent.isCancelled !== true) {
                throw new Error('Production workflow corrupted balances');
            }
            await assertFails(updateDoc(secondReceipt, { note: 'Edited', updatedAt: serverTimestamp() }));
            await assertFails(deleteDoc(secondReceipt));
        });
    });

    describe('FR11-13 monthly reporting queries', () => {
        const bounded = (db, owner, name, field, start = '2025-12-01', end = '2026-01-01') =>
            query(collection(db, 'users', owner, name), where(field, '>=', start), where(field, '<', end), orderBy(field));

        it('permits complete owner month queries across the year boundary for each transaction collection', async () => {
            const db = testEnv.authenticatedContext('alice').firestore();
            const fixtures = [
                ['expenses', 'expenseDate', { ...mockValidExpense }],
                ['incomeReceipts', 'receiptDate', { ...mockValidLkrIncome }],
                ['cryptoResults', 'resultDate', { direction: 'gain', amountMinor: 10000, note: '', lastOperationId: 'reporting-fixture' }]
            ];
            for (const [name, field, base] of fixtures) {
                for (const [id, date] of [['before', '2025-11-30'], ['first', '2025-12-01'], ['last', '2025-12-31'], ['next', '2026-01-01']]) {
                    await assertSucceeds(setDoc(doc(db, 'users', 'alice', name, id), {
                        ...base, [field]: date, createdAt: serverTimestamp(), updatedAt: serverTimestamp()
                    }));
                }
                const rows = await assertSucceeds(getDocs(bounded(db, 'alice', name, field)));
                if (rows.docs.map(row => row.id).join(',') !== 'first,last') throw new Error(`Incorrect ${name} month boundary`);
            }
            await assertSucceeds(getDocs(collection(db, 'users', 'alice', 'expectedPayments')));
        });

        it('backdating category correction and deletion change the actual bounded expense query', async () => {
            const db = testEnv.authenticatedContext('alice').firestore();
            const ref = doc(db, 'users', 'alice', 'expenses', 'reporting-edit');
            await setDoc(ref, { ...mockValidExpense, expenseDate: '2026-01-01', createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
            const december = bounded(db, 'alice', 'expenses', 'expenseDate');
            const january = bounded(db, 'alice', 'expenses', 'expenseDate', '2026-01-01', '2026-02-01');
            if (!(await getDocs(december)).empty || (await getDocs(january)).size !== 1) throw new Error('Initial reporting month incorrect');
            await assertSucceeds(updateDoc(ref, { expenseDate: '2025-12-31', categoryId: 'transport', updatedAt: serverTimestamp() }));
            const moved = await getDocs(december);
            if (moved.size !== 1 || moved.docs[0].data().categoryId !== 'transport' || !(await getDocs(january)).empty) throw new Error('Backdating/correction not reflected');
            await assertSucceeds(deleteDoc(ref));
            if (!(await getDocs(december)).empty) throw new Error('Deleted expense remains in reporting query');
        });

        it('denies unauthenticated and cross-user bounded queries and current parent queries', async () => {
            for (const db of [testEnv.unauthenticatedContext().firestore(), testEnv.authenticatedContext('bob').firestore()]) {
                for (const [name, field] of [['expenses', 'expenseDate'], ['incomeReceipts', 'receiptDate'], ['cryptoResults', 'resultDate']]) {
                    await assertFails(getDocs(bounded(db, 'alice', name, field)));
                }
                await assertFails(getDocs(collection(db, 'users', 'alice', 'expectedPayments')));
            }
        });
    });

    describe('FR07 realised crypto results', () => {
        const payload = (overrides = {}) => ({
            direction: 'gain', amountMinor: 6000000, resultDate: '2024-02-29', note: '',
            lastOperationId: 'crypto-op-1', createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides
        });
        const crypto = (db, owner = 'alice', id = 'crypto-1') => doc(db, 'users', owner, 'cryptoResults', id);

        it('accepts owner create read update and delete while preserving createdAt', async () => {
            const db = testEnv.authenticatedContext('alice').firestore();
            const ref = crypto(db);
            await assertSucceeds(setDoc(ref, payload()));
            const created = (await assertSucceeds(getDoc(ref))).data().createdAt;
            await assertSucceeds(updateDoc(ref, { direction: 'loss', amountMinor: 2500000,
                resultDate: '2023-12-31', note: 'Backdated result', lastOperationId: 'crypto-op-2', updatedAt: serverTimestamp() }));
            const updated = (await getDoc(ref)).data();
            if (!updated.createdAt.isEqual(created) || updated.direction !== 'loss') throw new Error('Crypto update changed creation time or sign');
            await assertSucceeds(deleteDoc(ref));
            await assertSucceeds(deleteDoc(ref));
        });

        it('accepts AT03 and keeps ordinary receipts expenses and project counters unchanged', async () => {
            const db = testEnv.authenticatedContext('alice').firestore();
            const expense = doc(db, 'users', 'alice', 'expenses', 'ordinary-expense');
            const income = doc(db, 'users', 'alice', 'incomeReceipts', 'ordinary-income');
            await testEnv.withSecurityRulesDisabled(async context => {
                await setDoc(doc(context.firestore(), expense.path), { amountMinor: 85000 });
                await setDoc(doc(context.firestore(), income.path), { lkrAmountMinor: 14030000 });
                await setDoc(doc(context.firestore(), 'users', 'alice', 'expectedPayments', 'existing'), { directReceivedAmountMinor: 20000 });
            });
            await assertSucceeds(setDoc(crypto(db), payload()));
            await assertSucceeds(setDoc(crypto(db, 'alice', 'loss'), payload({ direction: 'loss', amountMinor: 2500000 })));
            const rows = (await getDocs(collection(db, 'users', 'alice', 'cryptoResults'))).docs.map(s => s.data());
            const net = rows.reduce((sum, r) => sum + (r.direction === 'gain' ? r.amountMinor : -r.amountMinor), 0);
            if (net !== 3500000) throw new Error('AT03 net must equal +35000 LKR');
            await assertSucceeds(updateDoc(crypto(db), { amountMinor: 5000000, updatedAt: serverTimestamp(), lastOperationId: 'edited' }));
            await assertSucceeds(deleteDoc(crypto(db, 'alice', 'loss')));
            if ((await getDoc(expense)).data().amountMinor !== 85000 || (await getDoc(income)).data().lkrAmountMinor !== 14030000 ||
                (await getDoc(doc(db, 'users', 'alice', 'expectedPayments', 'existing'))).data().directReceivedAmountMinor !== 20000) {
                throw new Error('Crypto changed ordinary financial collections');
            }
        });

        it('denies all unauthenticated and cross-user CRUD including collection queries', async () => {
            const owner = testEnv.authenticatedContext('alice').firestore();
            await setDoc(crypto(owner), payload());
            for (const db of [testEnv.unauthenticatedContext().firestore(), testEnv.authenticatedContext('bob').firestore()]) {
                await assertFails(getDoc(crypto(db)));
                await assertFails(getDocs(collection(db, 'users', 'alice', 'cryptoResults')));
                await assertFails(setDoc(crypto(db, 'alice', 'forbidden'), payload()));
                await assertFails(updateDoc(crypto(db), { note: 'intrusion', updatedAt: serverTimestamp() }));
                await assertFails(deleteDoc(crypto(db)));
            }
        });

        it('denies zero negative fractional string and contradictory sign amounts', async () => {
            const db = testEnv.authenticatedContext('alice').firestore();
            for (const amountMinor of [0, -1, 1.5, '100', null]) await assertFails(setDoc(crypto(db), payload({ amountMinor })));
            for (const direction of ['profit', '', 1, null]) await assertFails(setDoc(crypto(db), payload({ direction })));
            await assertFails(setDoc(crypto(db), payload({ signedAmountMinor: -6000000 })));
            await assertSucceeds(setDoc(crypto(db), payload({ amountMinor: 1 })));
        });

        it('has no arbitrary money ceiling below the supported integer representation', async () => {
            const db = testEnv.authenticatedContext('alice').firestore();
            await assertSucceeds(setDoc(crypto(db), payload({ amountMinor: Number.MAX_SAFE_INTEGER })));
        });

        it('checks real calendar dates century leap rules formatting and future dates', async () => {
            const db = testEnv.authenticatedContext('alice').firestore();
            for (const resultDate of ['2023-02-29', '1900-02-29', '2024-04-31', '2024-00-01', '2024-13-01',
                '2024-01-00', '0000-01-01', '2024-1-01', '2024-01-1', '9999-12-31', '', 20240101, null]) {
                await assertFails(setDoc(crypto(db), payload({ resultDate })));
            }
            for (const resultDate of ['2000-02-29', '2024-02-29', '0001-01-01']) {
                await assertSucceeds(setDoc(crypto(db, 'alice', resultDate), payload({ resultDate })));
            }
            const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
            await assertSucceeds(setDoc(crypto(db, 'alice', 'today'), payload({ resultDate: today })));
            const tomorrow = new Date(`${today}T00:00:00Z`);
            tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
            await assertFails(setDoc(crypto(db), payload({ resultDate: tomorrow.toISOString().slice(0, 10) })));
        });

        it('requires exact schema bounded typed notes operation IDs and timestamps', async () => {
            const db = testEnv.authenticatedContext('alice').firestore();
            for (const field of ['direction', 'amountMinor', 'resultDate', 'note', 'lastOperationId', 'createdAt', 'updatedAt']) {
                const missing = payload(); delete missing[field]; await assertFails(setDoc(crypto(db), missing));
            }
            for (const overrides of [{ note: 'x'.repeat(501) }, { note: null }, { note: 1 }, { lastOperationId: '' },
                { lastOperationId: 'x'.repeat(129) }, { lastOperationId: 1 }, { currency: 'USD' }, { netTotal: 6000000 },
                { createdAt: new Date('2020-01-01') }, { updatedAt: new Date('2020-01-01') }]) {
                await assertFails(setDoc(crypto(db), payload(overrides)));
            }
            await assertSucceeds(setDoc(crypto(db), payload({ note: 'x'.repeat(500) })));
        });

        it('rejects invalid updates creation-time changes and extra link fields', async () => {
            const db = testEnv.authenticatedContext('alice').firestore();
            await setDoc(crypto(db), payload());
            for (const changes of [{ createdAt: new Date('2020-01-01') }, { amountMinor: 0 }, { amountMinor: -1 },
                { resultDate: '2025-02-29' }, { resultDate: '9999-01-01' }, { direction: 'other' },
                { linkedPaymentId: 'project' }, { note: 'x'.repeat(501) }, { updatedAt: new Date('2020-01-01') }]) {
                await assertFails(updateDoc(crypto(db), { updatedAt: serverTimestamp(), ...changes }));
            }
        });
    });
    require('./savings-tests')(() => testEnv);
});

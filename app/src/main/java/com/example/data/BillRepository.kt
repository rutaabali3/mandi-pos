package com.example.data

import android.content.Context
import com.example.R
import com.example.model.MandiBill
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BillRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeBills(userId: String): Flow<List<MandiBill>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch for bills query." }
        val collectionPath = "bills"
        emitAll(
            db.collection(collectionPath)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MandiBill::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, collectionPath)
                    }
                    throw error
                }
        )
    }

    suspend fun saveBill(userId: String, bill: MandiBill): Result<MandiBill> {
        var docPath = "bills"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }

            val cleanCustomer = bill.customerName.trim().ifBlank { "Cash Buyer" }.take(80)
            val cleanItem = bill.itemName.trim().ifBlank { "Potato / Aloo" }.take(80)
            val cleanBillNum = bill.billNumber.trim().take(40)
            require(cleanBillNum.isNotEmpty()) { "Bill number cannot be empty." }
            require(bill.weights.isNotEmpty() && bill.weights.size <= 500) {
                "Bill must contain between 1 and 500 bags."
            }
            require(bill.weights.all { it > 0.0 }) { "All bag weights must be positive." }
            require(bill.totalWeightKg > 0.0 && bill.totalWeightKg <= 500000.0) {
                "Total weight out of valid bounds."
            }
            require(bill.ratePerMann > 0.0 && bill.ratePerMann <= 10000000.0) {
                "Rate per Mann must be positive."
            }

            val docRef = db.collection("bills").document()
            docPath = docRef.path

            // Exclude @DocumentId 'id' from Firestore payload so CustomClassMapper deserializes cleanly
            val payload = mapOf(
                "userId" to uid,
                "billNumber" to cleanBillNum,
                "customerName" to cleanCustomer,
                "itemName" to cleanItem,
                "weights" to bill.weights,
                "totalBags" to bill.weights.size.toLong(),
                "totalWeightKg" to bill.totalWeightKg,
                "ratePerMann" to bill.ratePerMann,
                "finalCost" to bill.finalCost,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            Result.success(
                bill.copy(
                    id = docRef.id,
                    userId = uid,
                    customerName = cleanCustomer,
                    itemName = cleanItem,
                    billNumber = cleanBillNum,
                    localTimestampMillis = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBill(userId: String, billId: String): Result<Unit> {
        var docPath = "bills/$billId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user delete." }
            require(billId.isNotBlank()) { "Bill ID cannot be blank." }
            val docRef = db.collection("bills").document(billId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}

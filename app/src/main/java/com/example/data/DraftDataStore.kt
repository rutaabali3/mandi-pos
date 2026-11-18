package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.MandiBill
import com.example.model.MandiDraft
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.mandiDataStore by preferencesDataStore(name = "mandi_pos_prefs")

class DraftDataStore(private val context: Context) {

    private object Keys {
        val DRAFT_WEIGHTS = stringPreferencesKey("draft_weights_json")
        val MOJODA_RATE = stringPreferencesKey("mojoda_rate_input")
        val CUSTOMER_NAME = stringPreferencesKey("draft_customer_name")
        val ITEM_NAME = stringPreferencesKey("draft_item_name")
        val BILL_SEQ_TODAY = intPreferencesKey("bill_sequence_today")
        val SEQ_DATE_KEY = stringPreferencesKey("sequence_date_key")
        fun billsMirrorKey(userId: String) = stringPreferencesKey("bills_mirror_$userId")
    }

    suspend fun loadDraft(): MandiDraft {
        val prefs = context.mandiDataStore.data.first()
        val weights = parseWeightsJson(prefs[Keys.DRAFT_WEIGHTS])
        return MandiDraft(
            weights = weights,
            mojodaRateInput = prefs[Keys.MOJODA_RATE] ?: "",
            customerName = prefs[Keys.CUSTOMER_NAME] ?: "Cash Buyer",
            itemName = prefs[Keys.ITEM_NAME] ?: "Potato / Aloo",
            billSequenceToday = prefs[Keys.BILL_SEQ_TODAY] ?: 0,
            sequenceDateKey = prefs[Keys.SEQ_DATE_KEY] ?: ""
        )
    }

    suspend fun saveDraft(draft: MandiDraft) {
        val array = JSONArray()
        draft.weights.forEach { array.put(it) }
        context.mandiDataStore.edit { prefs ->
            prefs[Keys.DRAFT_WEIGHTS] = array.toString()
            prefs[Keys.MOJODA_RATE] = draft.mojodaRateInput
            prefs[Keys.CUSTOMER_NAME] = draft.customerName
            prefs[Keys.ITEM_NAME] = draft.itemName
            prefs[Keys.BILL_SEQ_TODAY] = draft.billSequenceToday
            prefs[Keys.SEQ_DATE_KEY] = draft.sequenceDateKey
        }
    }

    suspend fun loadMirroredBills(userId: String): List<MandiBill> {
        if (userId.isBlank()) return emptyList()
        val raw = context.mandiDataStore.data.first()[Keys.billsMirrorKey(userId)] ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val weightsArr = obj.optJSONArray("weights") ?: JSONArray()
                    val weightsList = buildList {
                        for (w in 0 until weightsArr.length()) {
                            add(weightsArr.optDouble(w, 0.0))
                        }
                    }
                    add(
                        MandiBill(
                            id = obj.optString("id", ""),
                            userId = obj.optString("userId", userId),
                            billNumber = obj.optString("billNumber", ""),
                            customerName = obj.optString("customerName", "Cash Buyer"),
                            itemName = obj.optString("itemName", "Potato / Aloo"),
                            weights = weightsList,
                            totalBags = obj.optLong("totalBags", weightsList.size.toLong()),
                            totalWeightKg = obj.optDouble("totalWeightKg", weightsList.sum()),
                            ratePerMann = obj.optDouble("ratePerMann", 0.0),
                            finalCost = obj.optDouble("finalCost", 0.0),
                            createdAt = null,
                            localTimestampMillis = obj.optLong("localTimestampMillis", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveMirroredBills(userId: String, bills: List<MandiBill>) {
        if (userId.isBlank()) return
        val arr = JSONArray()
        bills.take(250).forEach { bill ->
            val weightsArr = JSONArray()
            bill.weights.forEach { weightsArr.put(it) }
            val obj = JSONObject().apply {
                put("id", bill.id)
                put("userId", bill.userId)
                put("billNumber", bill.billNumber)
                put("customerName", bill.customerName)
                put("itemName", bill.itemName)
                put("weights", weightsArr)
                put("totalBags", bill.totalBags)
                put("totalWeightKg", bill.totalWeightKg)
                put("ratePerMann", bill.ratePerMann)
                put("finalCost", bill.finalCost)
                put("localTimestampMillis", bill.effectiveTimeMillis())
            }
            arr.put(obj)
        }
        context.mandiDataStore.edit { prefs ->
            prefs[Keys.billsMirrorKey(userId)] = arr.toString()
        }
    }

    private fun parseWeightsJson(raw: String?): List<Double> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optDouble(i, -1.0)
                    if (v > 0.0) add(v)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

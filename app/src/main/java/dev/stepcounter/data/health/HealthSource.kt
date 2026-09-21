package dev.stepcounter.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

class HealthSource(private val context: Context) {
    val availability get() = HealthConnectClient.getSdkStatus(context)
    private val client get() = HealthConnectClient.getOrCreate(context)
    val readPermission = HealthPermission.getReadPermission(StepsRecord::class)
    val backgroundPermission = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    fun backgroundSupported() = availability == HealthConnectClient.SDK_AVAILABLE &&
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
        HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    suspend fun permissions(): Set<String> = if (availability == HealthConnectClient.SDK_AVAILABLE)
        client.permissionController.getGrantedPermissions() else emptySet()
    suspend fun steps(date: LocalDate, zone: ZoneId): Long {
        val result = client.aggregate(AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant())
        ))
        return result[StepsRecord.COUNT_TOTAL] ?: 0L
    }
}

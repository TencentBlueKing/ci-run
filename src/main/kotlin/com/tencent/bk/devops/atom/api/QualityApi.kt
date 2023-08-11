package com.tencent.bk.devops.atom.api

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.common.QUALITY_ELEMENT_TYPE
import com.tencent.bk.devops.atom.pojo.request.IndicatorCreate
import com.tencent.bk.devops.atom.utils.json.JsonUtil
import com.tencent.bk.devops.plugin.pojo.Result
import okhttp3.RequestBody

class QualityApi : BaseApi() {

    private val urlPrefix = "/quality/api/build"

    fun upsertIndicator(
        userId: String,
        projectId: String,
        indicatorCreate: List<IndicatorCreate>
    ): Result<Boolean> {
        val url = "$urlPrefix/indicator/v3/project/$projectId/upsertIndicator"
        val requestBody = RequestBody.create(JSON_CONTENT_TYPE, JsonUtil.toJson(indicatorCreate))
        val request = buildPost(url, requestBody, mutableMapOf("X-DEVOPS-UID" to userId))
        val responseContent = request(request, "创建红线自定义指标失败")

        return JsonUtil.fromJson(responseContent, object : TypeReference<Result<Boolean>>() {})
    }

    fun saveScriptHisMetadata(
        taskId: String,
        taskName: String,
        data: Map<String, String>
    ): Result<Boolean> {
        val url = "$urlPrefix/metadata/saveHisMetadata" +
            "?elementType=$QUALITY_ELEMENT_TYPE&taskId=$taskId&taskName=$taskName"
        val requestBody = RequestBody.create(JSON_CONTENT_TYPE, JsonUtil.toJson(data))
        val request = buildPost(url, requestBody, mutableMapOf())
        val responseContent = request(request, "保存红线数据失败")

        return JsonUtil.fromJson(responseContent, object : TypeReference<Result<Boolean>>() {})
    }
}

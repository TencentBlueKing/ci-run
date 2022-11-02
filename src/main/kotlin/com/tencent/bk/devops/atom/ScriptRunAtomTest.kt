package com.tencent.bk.devops.atom

import com.tencent.bk.devops.atom.pojo.ScriptRunAtomParam
import junit.framework.TestCase

class ScriptRunAtomTest : TestCase() {

    private val atom = ScriptRunAtom()

    fun testRun() {
        atom.execute(AtomContext(ScriptRunAtomParam::class.java))
    }
/*
    fun testGetQualityDataType() {
        val cls = ScriptRunAtom()
        assertEquals(QualityDataType.INT, cls.getQualityDataType("0"))
        assertEquals(QualityDataType.FLOAT, cls.getQualityDataType("0.123"))
        assertEquals(QualityDataType.BOOLEAN, cls.getQualityDataType("true"))
        try {
            cls.getQualityDataType("xxx")
        } catch (e: TaskExecuteException) {
            assertEquals(e.errorMsg, "error qualityDataType: xxx")
        } catch (e: Exception) {
            assert(false)
        }
    }

    companion object {
        private val OUTPUT_NAME = Pattern.compile("name=([^,:=\\s]*)")
        private val OUTPUT_TYPE = Pattern.compile("type=([^,:=\\s]*)")
        private val OUTPUT_LABEL = Pattern.compile("label=([^,:=\\s]*)")
        private val OUTPUT_PATH = Pattern.compile("path=([^,:=\\s]*)")
        private val OUTPUT_REPORT_TYPE = Pattern.compile("reportType=([^,:=\\s]*)")
        private val OUTPUT_GATE_TITLE = Pattern.compile("title=([^,:=\\s]*)")
    }

    private fun getOutputMarcher(macher: Matcher): String? {
        return with(macher) {
            if (this.find()) {
                this.group(1)
            } else null
        }
    }

    fun appendOutputToFile(
        tmpLine: String,
        workspace: File?,
        resultLogFile: String?,
        stepId: String?
    ) {
        val pattenOutput = "::set-output\\s.*"
        val prefixOutput = "::set-output "
        if (Pattern.matches(pattenOutput, tmpLine)) {
            val value = tmpLine.removePrefix(prefixOutput)

            val nameMatcher = getOutputMarcher(OUTPUT_NAME.matcher(value)) ?: ""
            val typeMatcher = getOutputMarcher(OUTPUT_TYPE.matcher(value)) ?: "string" // type 默认为string
            val labelMatcher = getOutputMarcher(OUTPUT_LABEL.matcher(value)) ?: ""
            val pathMatcher = getOutputMarcher(OUTPUT_PATH.matcher(value)) ?: ""
            val reportTypeMatcher = getOutputMarcher(OUTPUT_REPORT_TYPE.matcher(value)) ?: ""


            val keyValue = value.split("::")
            if (keyValue.size >= 2) {
                // 以逗号为分隔符 左右依次为name type label path reportType
                println(
                    "$nameMatcher," +
                        "$typeMatcher," +
                        "$labelMatcher," +
                        "$pathMatcher," +
                        "$reportTypeMatcher=${value.removePrefix("${keyValue[0]}::")}\n"
                )
            }
        }
    }


    private fun appendGateToFile(
        tmpLine: String,
        list: MutableList<String>
    ) {
        val pattenOutput = "[\"]?::set-gate-value\\s(.*)"
        val prefixOutput = "::set-gate-value "
        if (Pattern.matches(pattenOutput, tmpLine)) {
            val value = tmpLine.removeSurrounding("\"").removePrefix(prefixOutput)
            val name = getOutputMarcher(OUTPUT_NAME.matcher(value))
            val title = getOutputMarcher(OUTPUT_GATE_TITLE.matcher(value))
            val keyValue = value.split("::")
            if (keyValue.size >= 2) {
                // pass_rate=1,pass_rate=通过率\n
                var text = "$name=${value.removePrefix("${keyValue[0]}::")}"
                if (!title.isNullOrBlank()) {
                    text = text.plus(",$name=$title")
                }
                list.add("$text")
            }
        }
    }

    fun testGateOutPut() {
        val testCases = listOf(
            "::set-gate-value name=pass_rate::0.9",
            "::set-gate-value name=pass_rate,title=测试用例通过率::0.9",
            "::set-gate-value name=pass_rate,title=测试全部通过率::0.1",
            "::set-gate-value name=errorCount,title=错误总数::100000.00001",
            "::set-gate-value name=IntTest,title=整数计数::100",
            "::set-gate-value name=isUpload,title=是否上传::true"
        )
        val expectResult = listOf(
            "pass_rate=0.9",
            "pass_rate=0.9,pass_rate=测试用例通过率",
            "pass_rate=0.1,pass_rate=测试全部通过率",
            "errorCount=100000.00001,errorCount=错误总数",
            "IntTest=100,IntTest=整数计数",
            "isUpload=true,isUpload=是否上传"
        )
        val gateFile = mutableListOf<String>()
        testCases.forEach {
            appendGateToFile(it, gateFile)
        }
        checkRealAndExpect(gateFile, expectResult)
        val data = mutableMapOf<String, String>()
        val title = mutableMapOf<String, String>()
        gateFile.forEach {
            val split = it.split(",")
            val nameToValue = split.getOrNull(0)
            val nameToTitle = split.getOrNull(1)
            keyEqualValueInsertMap(nameToValue, data)
            keyEqualValueInsertMap(nameToTitle, title)
        }
        data.values.forEach {
            getQualityDataType(it)
        }
        // titleCheck
        assertEquals("测试全部通过率", title["pass_rate"])
        assertEquals("错误总数", title["errorCount"])
        assertEquals("整数计数", title["IntTest"])
        assertEquals("是否上传", title["isUpload"])
        // dataCheck
        assertEquals("0.1", data["pass_rate"])
        assertEquals("true", data["isUpload"])
        assertEquals("100", data["IntTest"])
    }
    private fun keyEqualValueInsertMap(nameToValue: String?, map: MutableMap<String, String>) {
        if (nameToValue.isNullOrBlank()) return
        val key = nameToValue.split("=").getOrNull(0) ?: throw TaskExecuteException(
            errorMsg = "Illegal gateway key set: $nameToValue",
            errorType = ErrorType.USER,
            errorCode = ErrorCode.USER_INPUT_INVAILD
        )
        val value = nameToValue.split("=").getOrNull(1) ?: throw TaskExecuteException(
            errorMsg = "Illegal gateway key set: $nameToValue",
            errorType = ErrorType.USER,
            errorCode = ErrorCode.USER_INPUT_INVAILD
        )
        map[key] = value.trim()
    }


    private fun checkRealAndExpect(real: MutableList<String>, expect: List<String>) {
        real.forEachIndexed { index, _ ->
            assertEquals(expect[index], real[index])
        }
    }

    private fun getQualityDataType(value: String): QualityDataType {
        value.toIntOrNull().let {
            if (it != null) {
                return QualityDataType.INT
            }
        }
        value.toFloatOrNull().let {
            if (it != null) {
                return QualityDataType.FLOAT
            }
        }
        if (value == "true" || value == "false") {
            return QualityDataType.BOOLEAN
        }
        throw TaskExecuteException(
            errorMsg = "error qualityDataType: $value",
            errorType = ErrorType.USER,
            errorCode = ErrorCode.USER_INPUT_INVAILD
        )
    }

    fun testOutPut() {
        val a = "::set-output name=var_4,type=report,label=测试报告名称,reportType=THIRDPARTY::https://www.xxx.com/"
        val b = "::set-output name=var_3,type=report,label=测试报告名称,path=report/::index.html"
        val c = "::set-output name=var_2,type=artifact::*.txt"
        val d = "::set-output name=var_1::1"
        appendOutputToFile(a, null, null, null)
        appendOutputToFile(b, null, null, null)
        appendOutputToFile(c, null, null, null)
        appendOutputToFile(d, null, null, null)
    }


    fun testEnvUtils() {
        val command = "        echo \${{ ci.workspace }}\n" +
            "        echo envs.env_a=\${{ envs.env_a }}, env_a=\$env_a\n" +
            "        echo envs.env_b=\${{ envs.env_b }}, env_b=\$env_b\n" +
            "        echo envs.env_c=\${{ envs.env_c }}, env_c=\$env_c\n" +
            "        echo envs.env_d=\${{ envs.env_d }}, env_d=\$env_d\n" +
            "        echo envs.env_e=\${{ envs.env_e }}, env_e=\$env_e\n" +
            "        echo envs.a=\${{ envs.a }}, a=\$a\n" +
            "\n" +
            "        echo ::set-output name=a::i am a at step_1"
        val data = mapOf("envs.env_a" to "test")
//        print(command)
        val res = ReplacementUtils.replace(
            command = command,
            replacement = object : ReplacementUtils.KeyReplacement {
                override fun getReplacement(key: String): String? = if (data[key] != null) {
                    data[key]
                } else {
                    null
                }
            },
            contextMap = null
        )
        assertEquals(res, "        echo \\\${{ ci.workspace }}\n" +
            "        echo envs.env_a=test, env_a=\$env_a\n" +
            "        echo envs.env_b=\\\${{ envs.env_b }}, env_b=\$env_b\n" +
            "        echo envs.env_c=\\\${{ envs.env_c }}, env_c=\$env_c\n" +
            "        echo envs.env_d=\\\${{ envs.env_d }}, env_d=\$env_d\n" +
            "        echo envs.env_e=\\\${{ envs.env_e }}, env_e=\$env_e\n" +
            "        echo envs.a=\\\${{ envs.a }}, a=\$a\n" +
            "\n" +
            "        echo ::set-output name=a::i am a at step_1")
    }*/
}

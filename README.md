#### 插件功能
在构建机上执行脚本:
- 默认情况使用Bash进行脚本执行
- 可选解析器如图所示![command.png](./img/command.png)

#### 适用场景
执行编译脚本

支持通过如下方式设置当前步骤输出变量：
```
echo "::set-output name=<output_name>::<output_val>"
```
如：
```
echo "::set-output name=release_type::dev"
```
在下游步骤入参中:
- 通过 ${{ jobs.<job_id>.steps.<step_id>.outputs.release_type }} 引用此变量值
- <job_id> 为当前Job上配置的 Job ID
- <step_id> 为当前 Task 上配置的 Step ID

支持通过如下方式设置/修改流水线变量：
```
echo  "::set-variable name=<var_name>::<var_value>"
```
如：
```
echo  "::set-variable name=a::1"
```
在下游步骤入参中，通过 ${{ variables.a }} 方式引用此变量

#### 使用限制和受限解决方案[可选]
设置输出参数或者流水线变量时，**在当前步骤不会生效，在下游步骤才生效**

#### 常见的失败原因和解决方案
1. 脚本执行退出码非0时，当前步骤执行结果为失败，请检查脚本逻辑，或确认执行环境是否满足需求

#### 多行文本使用set-output/set-variable/set-gate-value
bash示例：
使用format_multiple_lines 替代echo输出
```
content=$(ls -l ..)
echo "$content"
format_multiple_lines "::set-output name=content_a::$content"
resultStr="\n"
resultStr="$resultStr\n$PATH"
resultStr="$resultStr\n$PATH"
resultStr="$resultStr\n$PATH"
echo resultStr=$resultStr
format_multiple_lines "::set-output name=content2_a::$resultStr"
```

python 示例：
```
multiple_lines = "line one \n line two \n line three"
print("::set-output name=lines::{0}".format(format_multiple_lines(multiple_lines)))
```

#### Plugin Functions
Execute the script on the build machine:
- uses Bash for script execution by default
- Optional parser as shown
${{indexFile("command.png")}}

#### Applicable scene
execute compile script

Supports setting current step output variables in the following ways:
```
echo "::set-output name=<output_name>::<output_val>"
```
Such as:
```
echo "::set-output name=release_type::dev"
```
In the downstream step into the parameter:
- Reference this variable value via ${{ jobs.<job_id>.steps.<step_id>.outputs.release_type }}
- <job_id> is the Job ID configured on the current Job
- <step_id> is the Step ID configured on the current Task

Supports setting/modifying pipeline variables in the following ways:
```
echo "::set-variable name=<var_name>::<var_value>"
```
Such as:
```
echo "::set-variable name=a::1"
```
In the input parameters of downstream steps, refer to this variable through ${{ variables.a }}

#### Using Restricted and Restricted Resolution [Optional]
When setting output parameters or pipeline variables, **will not take effect in the current step, but will take effect in the downstream steps**

#### Common failure causes and solutions
1. When the script execution exit code is not 0, the execution result of the current step is failure. Please check the script logic, or confirm whether the execution environment meets the requirements

#### Multi-line text uses set-output/set-variable/set-gate-value
bash example:
Use format_multiple_lines instead of echo output
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

python example:
```
multiple_lines = "line one \n line two \n line three"
print("::set-output name=lines::{0}".format(format_multiple_lines(multiple_lines)))
```
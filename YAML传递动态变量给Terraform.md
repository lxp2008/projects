
You: 

如何直接使用yaml 传递动态变量值给terraform

ChatGPT: 

要直接使用 YAML 文件传递动态变量值给 Terraform，可以通过以下步骤实现：
 
1. **创建 YAML 文件** : 创建一个包含变量值的 YAML 文件。例如，`variables.yaml` 文件内容如下：

```yaml
variables:
  region: us-west-2
  instance_type: t2.micro
  ami: ami-0c55b159cbfafe1f0
```
 
2. **编写 Python 脚本** : 使用 Python 脚本读取 YAML 文件并将其转换为 Terraform 可以使用的变量文件格式 (`.tfvars` 或 JSON 文件)。例如，创建一个名为 `yaml_to_tfvars.py` 的脚本：

```python
import yaml
import json

# 读取 YAML 文件
with open('variables.yaml', 'r') as yaml_file:
    data = yaml.safe_load(yaml_file)

# 提取变量
variables = data.get('variables', {})

# 写入 tfvars 文件
with open('variables.tfvars', 'w') as tfvars_file:
    for key, value in variables.items():
        tfvars_file.write(f'{key} = "{value}"\n')

# 或者写入 JSON 文件
with open('variables.json', 'w') as json_file:
    json.dump(variables, json_file, indent=2)
```
 
3. **运行 Python 脚本** : 在终端运行 Python 脚本生成 `variables.tfvars` 或 `variables.json` 文件：

```sh
python yaml_to_tfvars.py
```
 
4. **在 Terraform 中使用变量文件** : 在 Terraform 配置中使用生成的变量文件。可以通过命令行选项 `-var-file` 传递变量文件。例如：

```sh
terraform apply -var-file="variables.tfvars"
```

或者：


```sh
terraform apply -var-file="variables.json"
```

这样，您就可以通过 YAML 文件动态地传递变量值给 Terraform。
如果您希望避免使用 Python 脚本，可以直接在 Terraform 中使用外部数据源读取 YAML 文件并将其转换为变量。Terraform 支持通过 `external` 数据源执行外部脚本，例如： 
1. **创建一个 Bash 脚本** : 创建一个 Bash 脚本读取 YAML 文件并输出 JSON 格式的变量：

```sh
#!/bin/bash
python -c "import yaml, json, sys; print(json.dumps(yaml.safe_load(sys.stdin)['variables']))" < variables.yaml
```
 
2. **在 Terraform 配置中使用外部数据源** :

```hcl
data "external" "yaml_variables" {
  program = ["bash", "read_yaml.sh"]
}

locals {
  variables = data.external.yaml_variables.result
}

variable "region" {
  default = local.variables["region"]
}

variable "instance_type" {
  default = local.variables["instance_type"]
}

variable "ami" {
  default = local.variables["ami"]
}

# 使用变量
resource "aws_instance" "example" {
  ami           = var.ami
  instance_type = var.instance_type
  availability_zone = "us-west-2a"
}
```

在运行 Terraform 命令前，请确保脚本具有可执行权限：


```sh
chmod +x read_yaml.sh
```

这样，您就可以直接在 Terraform 配置中使用 YAML 文件中的动态变量。

You: 

如何使用yaml 传递动态变量值给terraform

ChatGPT: 

要使用 YAML 文件传递动态变量值给 Terraform，可以采用以下几种方法：

### 方法一：使用外部脚本读取 YAML 文件 
 
1. **创建 YAML 文件** : 创建一个包含变量值的 YAML 文件，例如 `variables.yaml`：

```yaml
variables:
  region: us-west-2
  instance_type: t2.micro
  ami: ami-0c55b159cbfafe1f0
```
 
2. **编写 Python 脚本** : 使用 Python 脚本读取 YAML 文件并输出 JSON 格式的变量。创建一个名为 `read_yaml.py` 的脚本：

```python
import yaml
import json
import sys

# 读取 YAML 文件
with open('variables.yaml', 'r') as yaml_file:
    data = yaml.safe_load(yaml_file)

# 输出 JSON 格式的变量
print(json.dumps(data['variables']))
```
 
3. **在 Terraform 中使用外部数据源** :

```hcl
data "external" "yaml_variables" {
  program = ["python3", "read_yaml.py"]
}

locals {
  variables = data.external.yaml_variables.result
}

variable "region" {
  default = local.variables["region"]
}

variable "instance_type" {
  default = local.variables["instance_type"]
}

variable "ami" {
  default = local.variables["ami"]
}

resource "aws_instance" "example" {
  ami           = var.ami
  instance_type = var.instance_type
  availability_zone = "us-west-2a"
}
```
 
4. **运行 Terraform 命令** :

```sh
terraform init
terraform apply
```
方法二：使用 `terraform-provider-external` 读取 YAML 文件 
1. **创建 YAML 文件** : 创建一个包含变量值的 YAML 文件，例如 `variables.yaml`（与上面相同）。
 
2. **编写 Bash 脚本** : 使用 Bash 脚本读取 YAML 文件并输出 JSON 格式的变量。创建一个名为 `read_yaml.sh` 的脚本：

```sh
#!/bin/bash
python3 -c "import yaml, json, sys; print(json.dumps(yaml.safe_load(open('variables.yaml'))['variables']))"
```
 
3. **在 Terraform 中使用外部数据源** :

```hcl
data "external" "yaml_variables" {
  program = ["bash", "read_yaml.sh"]
}

locals {
  variables = data.external.yaml_variables.result
}

variable "region" {
  default = local.variables["region"]
}

variable "instance_type" {
  default = local.variables["instance_type"]
}

variable "ami" {
  default = local.variables["ami"]
}

resource "aws_instance" "example" {
  ami           = var.ami
  instance_type = var.instance_type
  availability_zone = "us-west-2a"
}
```
 
4. **运行 Terraform 命令** :

```sh
chmod +x read_yaml.sh
terraform init
terraform apply
```
方法三：使用 Terraform 内置功能和 `terraform-provider-external` 
1. **创建 YAML 文件** : 创建一个包含变量值的 YAML 文件，例如 `variables.yaml`（与上面相同）。
 
2. **编写 Python 脚本** : 使用 Python 脚本读取 YAML 文件并输出 JSON 格式的变量。创建一个名为 `read_yaml.py` 的脚本（与上面相同）。
 
3. **在 Terraform 配置中引用外部脚本** :

```hcl
provider "external" {}

data "external" "yaml_variables" {
  program = ["python3", "read_yaml.py"]
}

locals {
  variables = data.external.yaml_variables.result
}

variable "region" {
  default = local.variables["region"]
}

variable "instance_type" {
  default = local.variables["instance_type"]
}

variable "ami" {
  default = local.variables["ami"]
}

resource "aws_instance" "example" {
  ami           = var.ami
  instance_type = var.instance_type
  availability_zone = "us-west-2a"
}
```
 
4. **运行 Terraform 命令** :

```sh
terraform init
terraform apply
```

通过以上任一方法，您可以使用 YAML 文件动态传递变量值给 Terraform。

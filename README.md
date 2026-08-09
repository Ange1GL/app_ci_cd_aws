# Despliegue continuo con GitHub Actions y AWS ECS


La infraestructura utilizada incluye:

- Amazon ECR para almacenar la imagen Docker.
- Amazon ECS para ejecutar y administrar el contenedor.
- AWS Fargate como capacidad de cómputo serverless para ECS.
- Application Load Balancer para exponer la aplicación hacia Internet.
- IAM + OIDC para que GitHub Actions pueda autenticarse en AWS sin almacenar Access Keys permanentes.
- GitHub Actions para construir la imagen, publicarla en ECR y actualizar el servicio ECS.

---

## 1. Crear el repositorio en Amazon ECR

Se creó un repositorio privado llamado:

```text
app-aws
```

Este repositorio almacena las imágenes Docker utilizadas por ECS.

La URI sigue este formato:

```text
<AWS_ACCOUNT_ID>.dkr.ecr.<AWS_REGION>.amazonaws.com/app-aws
```

Ejemplo de construcción de la imagen:

```bash
docker build -t app-aws:latest .
```

---

## 2. Crear un clúster de Amazon ECS

Se creó un clúster ECS utilizando AWS Fargate.

Un **clúster ECS** es el espacio lógico donde AWS administra los servicios y tareas de la aplicación.

Para esta práctica se utilizó:

```text
app-aws-cluster
```

---

## 3. Crear una Task Definition

Una **Task Definition** es la plantilla que describe cómo debe ejecutarse uno o varios contenedores dentro de ECS.

En ella se definen elementos como:

- Imagen Docker.
- CPU y memoria.
- Puerto del contenedor.
- Variables de entorno.
- Configuración de logs.
- Roles IAM.
- Health checks.
- Configuración de red.

La familia utilizada en esta práctica es:

```text
app-task
```

El contenedor se configuró con:

```text
Nombre: main-8081
Puerto del contenedor: 8081
Protocolo: TCP
Protocolo de aplicación: HTTP
```

La aplicación Spring Boot escucha internamente en:

```text
8081
```

Por esta razón, el `containerPort` de ECS debe coincidir con ese puerto.

---

## 4. Crear el ECS Service

Un **ECS Service** mantiene una cantidad determinada de tareas ejecutándose de forma continua.

Por ejemplo:

```text
Desired tasks: 1
```

Esto significa que ECS intentará mantener una instancia activa de la aplicación.

El servicio utilizado en esta práctica es:

```text
app-task-service-phk6atw4
```

El Service utiliza:

- La Task Definition `app-task`.
- AWS Fargate.
- El clúster `app-aws-cluster`.
- Un Application Load Balancer.
- Un Target Group.

---

## 5. Configurar el Application Load Balancer

El Application Load Balancer recibe las peticiones provenientes de Internet y las reenvía hacia las tareas ECS.

La configuración utilizada fue:

```text
Listener del ALB: HTTP :80
Target Group: HTTP :8081
Contenedor ECS: main-8081 :8081
```

El puerto `80` corresponde al puerto público donde escucha el Load Balancer.

El puerto `8081` corresponde al puerto interno donde escucha la aplicación Spring Boot.

La relación queda:

```text
ALB escucha en puerto 80.
Target Group reenvía al puerto 8081.
Spring Boot escucha en puerto 8081.
```

Esta aplicación tiene esta configuración

```properties
server.port=8081
server.servlet.context-path=/api
```

y aparte Spring Boot Actuactor esta habilitado

```text
/api/actuator/health
```

---

## 6. Configurar OIDC entre GitHub Actions y AWS

Para evitar almacenar credenciales permanentes como:

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
```

se configuró autenticación mediante OIDC.

En AWS IAM se creó un Identity Provider con:

```text
Provider URL:
https://token.actions.githubusercontent.com

Audience:
sts.amazonaws.com
```

OIDC permite que GitHub Actions obtenga credenciales temporales de AWS mediante AWS STS.

---

## 7. Crear un IAM Role para GitHub Actions

Se creó el rol:

```text
ECSDeployRole
```

Este rol es asumido por GitHub Actions mediante OIDC.

Debe contar con permisos para:

- Autenticarse contra ECR.
- Subir imágenes a ECR.
- Consultar Task Definitions.
- Registrar nuevas revisiones.
- Actualizar ECS Services.
- Ejecutar `iam:PassRole` cuando la Task Definition utiliza un Execution Role.

---

## 8. Configurar la relación de confianza del IAM Role

Además de los permisos del rol, es necesario configurar su **Trust Policy**.

La Trust Policy determina quién puede asumir el rol.

Para GitHub Actions se utiliza:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<AWS_ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:<GITHUB_OWNER>@<GITHUB_OWNER_ID>/<GITHUB_REPOSITORY>@<GITHUB_REPOSITORY_ID>:ref:refs/heads/<GITHUB_BRANCH>"
        }
      }
    }
  ]
}
```

### ¿Por qué fue necesario modificar `sub`?

GitHub utiliza el claim `sub` del token OIDC para identificar de qué repositorio y referencia proviene el workflow.

En repositorios que utilizan el formato OIDC inmutable actual, GitHub puede incluir identificadores numéricos del propietario y del repositorio.

Por ello, en lugar de utilizar únicamente:

```text
repo:<OWNER>/<REPOSITORY>:ref:refs/heads/main
```

puede ser necesario utilizar:

```text
repo:<OWNER>@<OWNER_ID>/<REPOSITORY>@<REPOSITORY_ID>:ref:refs/heads/main
```

Si el valor configurado en AWS no coincide exactamente con el `sub` enviado por GitHub, AWS devuelve:

```text
Not authorized to perform sts:AssumeRoleWithWebIdentity
```

### Obtener los identificadores desde GitHub Actions

Temporalmente se puede agregar este paso al workflow:

```yaml
- name: Debug GitHub context
  run: |
    echo "Repository = ${{ github.repository }}"
    echo "Repository ID = ${{ github.repository_id }}"
    echo "Owner = ${{ github.repository_owner }}"
    echo "Owner ID = ${{ github.repository_owner_id }}"
    echo "Ref = ${{ github.ref }}"
    echo "Event = ${{ github.event_name }}"
```

Con esos valores se puede construir correctamente el `sub` de la Trust Policy.

> El nombre público de un repositorio de GitHub no suele ser un secreto. Aun así, para que esta guía sea reutilizable y para evitar exponer identificadores de cuenta innecesarios, se utilizan variables como `<GITHUB_REPOSITORY_ID>` y `<AWS_ACCOUNT_ID>`.

---

## 9. Obtener el JSON completo de la Task Definition

La acción:

```text
aws-actions/amazon-ecs-render-task-definition
```

necesita una **Task Definition completa**, no únicamente el JSON de un contenedor.

Un archivo incorrecto sería:

```json
{
  "name": "main-8081",
  "image": "...",
  "portMappings": []
}
```

Esto representa únicamente un contenedor.

La Task Definition completa debe contener, entre otras propiedades:

```json
{
  "family": "app-task",
  "containerDefinitions": [
    {
      "name": "main-8081",
      "image": "...",
      "portMappings": [
        {
          "containerPort": 8081,
          "hostPort": 8081,
          "protocol": "tcp"
        }
      ]
    }
  ]
}
```

Para descargar la Task Definition actualmente registrada en AWS:

```bash
aws ecs describe-task-definition \
  --task-definition app-task \
  --region us-east-1 \
  --query taskDefinition \
  > task-definition.json
```

En PowerShell:

```powershell
aws ecs describe-task-definition `
  --task-definition app-task `
  --region us-east-1 `
  --query taskDefinition `
  > task-definition.json
```

Después se puede guardar en el repositorio como:

```text
.aws/task-definition.json
```

y agregarlo al control de versiones:

```bash
git add .aws/task-definition.json
git commit -m "Add ECS task definition"
git push
```

---

## 10. Crear el workflow de GitHub Actions

Crear:

```text
.github/workflows/aws.yml
```

Ejemplo:

```yaml
name: Deploy to Amazon ECS

on:
  push:
    branches:
      - <GITHUB_BRANCH>

permissions:
  contents: read
  id-token: write

env:
  AWS_REGION: <AWS_REGION>
  ECR_REPOSITORY: app-aws
  ECS_CLUSTER: app-aws-cluster
  ECS_SERVICE: app-task-service-phk6atw4
  ECS_TASK_DEFINITION: .aws/task-definition.json
  CONTAINER_NAME: main-8081

jobs:
  deploy:
    name: Deploy
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v6
        with:
          role-to-assume: arn:aws:iam::<AWS_ACCOUNT_ID>:role/ECSDeployRole
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build, tag and push image to ECR
        id: build-image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          echo "image=$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG" >> $GITHUB_OUTPUT

      - name: Update ECS task definition
        id: task-def
        uses: aws-actions/amazon-ecs-render-task-definition@v1
        with:
          task-definition: ${{ env.ECS_TASK_DEFINITION }}
          container-name: ${{ env.CONTAINER_NAME }}
          image: ${{ steps.build-image.outputs.image }}

      - name: Deploy to Amazon ECS
        uses: aws-actions/amazon-ecs-deploy-task-definition@v2
        with:
          task-definition: ${{ steps.task-def.outputs.task-definition }}
          service: ${{ env.ECS_SERVICE }}
          cluster: ${{ env.ECS_CLUSTER }}
          wait-for-service-stability: true
```

---

## 11. Variables dinámicas del workflow

- `<GITHUB_BRANCH>`: rama que dispara el deploy. Ejemplo: `main`
- `<AWS_REGION>`: región de AWS. Ejemplo: `us-east-1`
- `<AWS_ACCOUNT_ID>`: ID de la cuenta AWS
- `<GITHUB_OWNER>`: usuario u organización propietaria del repositorio
- `<GITHUB_OWNER_ID>`: ID numérico del usuario u organización de GitHub
- `<GITHUB_REPOSITORY>`: nombre del repositorio
- `<GITHUB_REPOSITORY_ID>`: ID numérico del repositorio
- `<ECR_REPOSITORY>`: nombre del repositorio de Amazon ECR
- `<ECS_CLUSTER>`: nombre del clúster de ECS
- `<ECS_SERVICE>`: nombre del servicio ECS
- `<TASK_DEFINITION_PATH>`: ruta del JSON de la Task Definition
- `<CONTAINER_NAME>`: nombre del contenedor dentro de `containerDefinitions`
- `<IAM_ROLE>`: rol IAM que GitHub Actions asumirá mediante OIDC

---

## 12. Resultado del Continuous Deployment

Una vez configurado el workflow, cada `push` realizado a la rama configurada provoca que GitHub Actions:

1. Descargue el código del repositorio.
2. Se autentique en AWS mediante OIDC.
3. Inicie sesión en Amazon ECR.
4. Construya una nueva imagen Docker.
5. Etiquete la imagen utilizando el SHA del commit.
6. Publique la imagen en `app-aws`.
7. Actualice la imagen dentro de la Task Definition.
8. Registre una nueva revisión de `app-task`.
9. Actualice `app-task-service-phk6atw4`.
10. Espere a que ECS confirme la estabilidad del nuevo despliegue.

El uso de `${{ github.sha }}` como tag permite identificar exactamente qué commit produjo cada imagen desplegada.

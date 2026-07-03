// main.bicep
param location string = 'eastus'
param appName string = 'appcontainer'
param acrName string = 'appcontainerm2m'
param imageName string = 'spring-app'
param imageTag string = 'latest'

// ACR
resource acr 'Microsoft.ContainerRegistry/registries@2023-01-01-preview' = {
  name: acrName
  location: location
  sku: { name: 'Basic' }
  properties: { adminUserEnabled: true }
}

// Container App Environment
resource environment 'Microsoft.App/managedEnvironments@2023-05-01' = {
  name: '${appName}-env'
  location: location
  properties: {}
}

// Container App
resource containerApp 'Microsoft.App/containerApps@2023-05-01' = {
  name: appName
  location: location
  properties: {
    managedEnvironmentId: environment.id
    configuration: {
      ingress: {
        external: true
        targetPort: 8080
      }
      registries: [{
        server: acr.properties.loginServer
        username: acr.listCredentials().username
        passwordSecretRef: 'acr-password'
      }]
    }
    template: {
      containers: [{
        name: appName
        image: '${acr.properties.loginServer}/${imageName}:${imageTag}'
        resources: {
          cpu: json('0.5')
          memory: '1Gi'
        }
      }]
      scale: {
        minReplicas: 1
        maxReplicas: 3
      }
    }
  }
}
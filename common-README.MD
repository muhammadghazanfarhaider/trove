## Parameters


The following tables lists the configurable parameters of the common chart and their default values.

| Parameter | Description | Default | required |
| --- | --- | --- | --- |
| `common.nameOverride` | app name | CHANGEME | YES |
| `common.image.repository` |  | `nil` | YES |
| `common.replicaCount` |  | 1 |  |
| `common.ports` |  | `{}` |  |
| `common.image.repository` |  | `nil` |  |
| `common.image.tag` |  | `lastest` |  |
| `common.image.extraEnv` |  | `{}` |  |
| `common.image.extraENVRulehandled` |  | `{}` |  |
| `common.image.envs.enabled` |  | `false` |  |
| `common.service` |  | `{}` | YES |
| `common.istio.virtualservice.enabled` |  | `true` |  |
| `common.istio.virtualservice.http` |  | `{}` |  |

## useage

1. copy template to local
```bash
cd targetplatform/Template/HelmChat
cp -r CHANGEME /path/to/local/APPNAME
```
2. update the values.yaml
 **update APPNAME,REPOADDR,LISTEN-PORT**
```bash
APPNAME=<app name>
LISTEN_PORT=<port number>
REPOADDR=<registry/namespace/repository> 
HOSTNAME=<virtualservice host name>
sed -i "/APPNAME/${APPNAME}"  values.yaml
sed -i "/REPOADDR/${REPOADDR}"  values.yaml
sed -i "/LISTEN_PORT/${LISTEN_PORT}"  values.yaml
sed -i "/HOSTNAME/${HOSTNAME}"  values.yaml
```
 **set env**  

 you can set `env` with `extraEnv` and `extraENVRulehandled` like blow  
 
 ```yaml
 common:
   image:
     extraEnv: 
       OSSKey:     "xxxx"
     extraENVRulehandled:
       SSO_url:    https://sso.$baseUrl
 ```
 **custom command**  

 you can set `command` with `extraCommand` like blow  
 ```yaml
 common:
   image:
     extraCommand: 
     - /usr/local/bin/meerastorage
     - --oidc-provider-addr=$(SSO_url)
     - --ac-grpc-addr=$(ACCESSCONTROL_grpcAddr)
     - --configurator-grpc-addr=$(CONFIGURATOR_grpcAddr)
     - --redis-addr=$(REDIS_svcAddr)
 ```
**livenessProbe and readinessProbe**  

you can set `livenessProbe` and `livenessProbe` like blow 
```yaml
common:
...
  readinessProbe:
    httpGet:
      scheme: HTTP
      path: /healthy
      port: 8080
    initialDelaySeconds: 30
    periodSeconds: 3
    failureThreshold: 3
  livenessProbe:
    httpGet:
      scheme: HTTP
      path: /healthy
      port: 8080
  initialDelaySeconds: 30
  periodSeconds: 3
  failureThreshold: 3
```

## how to add compoment

1. copy a template  

```
cp -r charts/common charts/common2  
```
2. add dependencies in Chart.yaml  
```yaml
dependencies:
...
  - name: common2
    repository: "file://../common2"
    condition: common2.enabled
```
3. update app chart name `charts/common2/Chart.yaml`  

```yaml
...
name: common2
...
```
4. update the `CHANGEME/values.yaml` for added compoment  
```yaml
...
common:
  enabled: true
...
common2:
  enabled: true
  nameOverride:  APPNAME2
...
```

5. add the  OS-env under the deployment.yaml

```
          env:
          - name: MY_POD_NAMESPACE
            valueFrom:
              fieldRef:
                fieldPath: metadata.namespace 
```                
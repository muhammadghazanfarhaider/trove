# 
## Introduction

Introduce your program


<!-- List service dependencies -->
## Dependencies

example， please replace it
```yaml
  - name: sso-be
    version: 3.1.1
  - name: accesscontrol-be
    version: 3.0.0
  - name: configurator-be
    version: 1.0.0
  - name: profile-be
    version: 1.2.9
  - name: ws-bus (optional)
    version: 1.0.0
```

## Upgrading an existing Release to a new major version

> Supported upgrade paths: 
``` 
2.2.12 (or older) -> 2.13.0 (or newer)
6.1.1（or older) -> 6.1.1 (or newer) 
```
## migration guide (there is an incompatible breaking change needing manual actions):

### To 2.13.0
need add a static user in sso service
```
- email: "notifier@example.com"
    # bcrypt hash of the string "password"
    hash: "$2a$10$2b2cU8CPhOTaGrs1HRQuAueS7JTT5ZHsHSzYiFPm1leZck7Mc8T4W"
    username: "notifier"
    userID: "notifier"

```

### To 6.1.1
this tag move file from meerafs to meerastorage
this tag move old task planner event to new task planner event

### To 6.8.0
this tag remove old version's planner grpc, and Venue depends on old version planner grpc interfaces to migrate old 
planner agenda data into new planner. 
To enable Venue agenda migration, restore to version before 6.8.0

## Installing the Chart
```sh
nameSpace=devops-meeraspace
workspaceHost=ops.meeraspace.com
nameSpacePrefix=`echo $nameSpace | awk -F "-" '{print $1}'` && echo " nameSpacePrefix is $nameSpacePrefix "  ## return  devops
nameSpaceMid=`echo $nameSpace | awk -F "-" '{print $2}'` && echo " nameSpaceMid is $nameSpaceMid "  ## return  meerasapce
hostPrefix=`echo $workspaceHost | awk -F "." '{print $1}'` && echo " hostPrefix is $hostPrefix "  ## return  ops
baseUrl=`echo ${workspaceHost#$hostPrefix.}` && echo " baseUrl is $baseUrl "   ## return meeraspace.com
```
To install the chart with the release name `release-name` & the global.dev `yours` :
```bash
helm3 install release-name  --set global.env=$yours,global.mode.standard=false,global.namespace.prefix=$nameSpacePrefix,global.namespace.mid=$nameSpaceMid,global.host.prefix=$hostPrefix,global.host.baseUrl=$baseUrl \
-n $nameSpace-system meeraspace-stable/${chartName} --version $version
```

## Parameters
The following table lists the configurable parameters of the workspace-be chart and their default values.

| Parameter                                     | Description                                                                                                                                         | Default                                                 |
|-----------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| `global.env`                                  | Global Docker registry secret names as an array                                                                                                     | `dev` |
| `global.mode.standard `                       | Global Docker registry secret names as an array                                                                                                | `true` |
| `image.repository`                            |                                                                                                            | registry.gitlab.com/target-digital-transformation/meera-ws               |
| `image.tag`                                   | Redis Image tag                                                                                                                                     | `{TAG_NAME}`                                            |
| `image.config.DATABASE`                       | Database name                                                                                                                                     | ws                                            |
| `istio.virtualservice.enabled`                | Use virtualservice                                                                                                                                  | `true`                                                  |
| `replicaCount`                          | Number of pods                                                                                                                                    | `1`                                                     |
| `accelerate-timed-tasks`                          | make timed task do every minutes                                                                                                                               |                                                      |
| `ws-bus`                          | connect to ws-bus service that complete the real-time push of message, it is the task manager's requirement                                                                                                                              |                                                      |
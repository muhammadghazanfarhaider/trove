# Common              

## Introduction


## Dependencies



## Upgrading

> Supported upgrade paths: 
``` 
0.2.0  -> latest
```

### To 0.2.0
add apply strategy: Recreate when pod using PVC
```yaml
  {{- if .Values.persistentVolumeClaim1.enabled }}
  strategy: Recreate
  {{- end }} 
```

### To 0.2.1
add optionalCommand & optionalEnv, remove the extraCommand1
```yaml
  {{- with .Values.image.optionalCommand }}
  {{- . | toYaml | nindent 10 }}
  {{- end }}

  {{- range $key, $value := .Values.image.optionalEnv }}
  - name: {{ $key }}
    value: {{ $value | quote }}
  {{- end }}  
```

### To 0.2.2

add istio virtualservice extra_host for specific project

add .istio.virtualservice.extra_host to values.yaml

```
istio:
  virtualservice:
    enabled: true
    host: CHANGME1xxx
    extra_host: foo.com
```



- add .common.istio.virtualservice.extra_host to values.yaml

  Eg:

```
common:
  enabled: true
  nameOverride:  CHANGEME_APPNAME
  envs:
    enabled: false
  istio:
    virtualservice:
      enabled: true   
      host: CHANGEME
      extra_host: foo.com
```



- add charts/common/templates/virtualservice.yaml:

```
    {{ if .Values.istio.virtualservice.extra_host }}
  - {{ .Values.istio.virtualservice.extra_host }}
    {{ end }}
```



- add templates/virtualservice.yaml:

```
    {{ if .Values.istio.virtualservice.extra_host }}
  - {{ .Values.istio.virtualservice.extra_host }}
    {{ end }}
```

### To 0.2.3
- add grpcHostUrl to _helpers.tpl Line 21-31
```
{{ define "grpcHostUrl" }}
    {{- if  and .Values.istio.virtualserviceGrpc.host  .Values.global.hostPrefix .Values.global.hostSuffix  }}
    {{- printf "%s%s%s.%s" .Values.global.hostPrefix .Values.istio.virtualserviceGrpc.host .Values.global.hostSuffix .Values.global.baseUrl -}}
      baseUrl
    {{- else }}
        {{- if  and .Values.istio.virtualserviceGrpc.host  .Values.global.hostPrefix }}
        {{- printf "%s%s.%s" .Values.global.hostPrefix .Values.istio.virtualserviceGrpc.host   .Values.global.baseUrl -}}
    {{- else }}
        {{- if  and .Values.istio.virtualserviceGrpc.host  .Values.global.hostSuffix }}
        {{- printf "%s%s.%s"  .Values.istio.virtualserviceGrpc.host  .Values.global.hostSuffix .Values.global.baseUrl -}}
    {{- else }}
        {{- if  .Values.istio.virtualserviceGrpc.host  }}
        {{- printf "%s.%s"  .Values.istio.virtualserviceGrpc.host  .Values.global.baseUrl -}}
    {{- else }}
        {{- print .Values.global.baseUrl }}
    {{- end }}
    {{- end }}
    {{- end }}
    {{- end }}
{{- end }}
```

### To 0.2.4

add imageRegistryOverride

image Registry priority is common.image.imageRegistryOverride > global.imageRegistryOverride > common.image.repository
- add to _helpers.tpl:
```
{{- define "APP.imageName" -}}
{{- if .Values.image.imageRegistryOverride -}}
    {{ $domain := (split "/" .Values.image.repository)._0 }}
    {{- printf "%s:%s" (.Values.image.repository| replace $domain .Values.image.imageRegistryOverride) .Values.image.tag -}}
{{- else if .Values.global.imageRegistryOverride -}}
    {{ $domain := (split "/" .Values.image.repository)._0 }}
    {{- printf "%s:%s" (.Values.image.repository| replace $domain .Values.global.imageRegistryOverride) .Values.image.tag -}}
{{- else -}}
    {{- printf "%s:%s" .Values.image.repository .Values.image.tag -}}
{{- end -}}
{{- end -}}
```

- change deployment.yaml:
```
 template.spec.containers.image: "{{ template "APP.imageName" .}}"
```


### To 0.2.6

- change deployment.yaml:
add following to support volumeMounts for initContainer
```
{{- if  .Values.initContainer.volumeMounts.enabled }}
          volumeMounts:
{{ toYaml .Values.initContainer.volumeMounts.volumeMounts | indent 12 }}
{{- end }}
```
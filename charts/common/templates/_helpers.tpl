{{- define "APP.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "APP.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "APP.version" -}}
{{- default .Values.image.tag | trunc 63 | trimSuffix "-" | quote -}}
{{- end -}}

{{- define "APP.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

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

# {{- define "APP.namespace" -}}
# {{ .Values.global.nsPrefix }}-{{- .Chart.Keywords | toString |  regexFind "[a-zA-Z0-9].*[a-zA-Z0-9]" -}}
# {{- end -}}

{{ define "hostUrl" }}
    {{- if  and .Values.istio.virtualservice.host  .Values.global.hostPrefix .Values.global.hostSuffix  }}
    {{- printf "%s%s%s.%s" .Values.global.hostPrefix .Values.istio.virtualservice.host .Values.global.hostSuffix .Values.global.baseUrl -}}
    {{- else }}
        {{- if  and .Values.istio.virtualservice.host  .Values.global.hostPrefix }}
        {{- printf "%s%s.%s" .Values.global.hostPrefix .Values.istio.virtualservice.host   .Values.global.baseUrl -}}
    {{- else }}
        {{- if  and .Values.istio.virtualservice.host  .Values.global.hostSuffix }}
        {{- printf "%s%s.%s"  .Values.istio.virtualservice.host  .Values.global.hostSuffix .Values.global.baseUrl -}}
    {{- else }}
        {{- if .Values.istio.virtualservice.host  }}
        {{- printf "%s.%s"  .Values.istio.virtualservice.host  .Values.global.baseUrl -}}
    {{- else }}
        {{- print .Values.global.baseUrl }}
    {{- end }}
    {{- end }}
    {{- end }}
    {{- end }}
{{- end }}

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


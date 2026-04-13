{{/*
Common labels for all resources.
*/}}
{{- define "numera.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ $.Release.Name }}
app.kubernetes.io/version: {{ $.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ $.Release.Service }}
helm.sh/chart: {{ $.Chart.Name }}-{{ $.Chart.Version | replace "+" "_" }}
{{- range $key, $val := $.Values.global.labels }}
{{ $key }}: {{ $val }}
{{- end }}
{{- end -}}

{{/*
Selector labels for a component.
*/}}
{{- define "numera.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ $.Release.Name }}
{{- end -}}

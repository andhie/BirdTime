# ML Kit discovers these registrars through manifest metadata and reflection.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}

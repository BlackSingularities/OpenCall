# Compose i AndroidX dostarczają własne reguły konsumenckie.
-keepattributes *Annotation*, Signature, InnerClasses

# Usługi telefonii są tworzone przez system po nazwie klasy z manifestu.
-keep class com.blacksingularities.opencall.call.** extends android.app.Service { *; }
-keep class com.blacksingularities.opencall.**.*Receiver { *; }

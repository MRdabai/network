# Add project specific ProGuard rules here.
-keep class com.weaknet.simulator.vpn.** { *; }
-keepclassmembers class * extends android.net.VpnService { *; }

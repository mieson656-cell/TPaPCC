package com.tpappcc

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class TpaPccAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The service is enabled explicitly by the device owner.
        // Remote actions can be added here without granting hidden access.
    }

    override fun onInterrupt() = Unit
}

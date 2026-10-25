package com.example.keymapper.injection

import android.view.InputEvent

interface Injector {
    fun inject(event: InputEvent): Boolean
}

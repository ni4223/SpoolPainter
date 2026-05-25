package com.spoolpainter.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// NfcAdapterWrapper provider lands in U4.
@Module
@InstallIn(SingletonComponent::class)
object NfcModule

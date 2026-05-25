package com.spoolpainter.app.di

import android.content.Context
import android.nfc.NfcAdapter
import com.spoolpainter.app.hardware.nfc.NfcAdapterWrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.datetime.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NfcModule {

    @Provides
    @Singleton
    fun provideNfcAdapter(@ApplicationContext context: Context): NfcAdapter? =
        NfcAdapter.getDefaultAdapter(context)

    @Provides
    @Singleton
    fun provideNfcAdapterWrapper(
        adapter: NfcAdapter?,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): NfcAdapterWrapper = NfcAdapterWrapper(adapter, dispatcher)

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.System
}

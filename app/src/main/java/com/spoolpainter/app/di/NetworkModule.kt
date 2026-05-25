package com.spoolpainter.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// OkHttp / Retrofit / SpoolmanApi providers land in U3.
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule

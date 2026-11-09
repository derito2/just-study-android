package com.dero.juststudykorean.data.processing

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProcessingModule {
    @Binds abstract fun pdfReader(reader: MuPdfReader): PdfReader
}

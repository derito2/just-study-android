package com.dero.juststudykorean.data.processing

import com.dero.juststudykorean.ai.GateGuard
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProcessingModule {
    @Binds abstract fun pdfReader(reader: MuPdfReader): PdfReader

    /** No Gemini Nano with a PDF in the queue (R-013). */
    @Binds abstract fun gateGuard(queue: ProcessingQueue): GateGuard
}

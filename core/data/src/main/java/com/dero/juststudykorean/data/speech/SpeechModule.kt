package com.dero.juststudykorean.data.speech

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** D9-3: the speech of the spoken practice is engine B (the platform's on-device recognizer). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechModule {
    @Binds abstract fun speech(engine: PlatformKoreanSpeech): KoreanSpeech
}

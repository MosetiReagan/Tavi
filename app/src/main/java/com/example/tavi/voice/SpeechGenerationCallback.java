package com.example.tavi.voice;

import kotlin.jvm.functions.Function1;
import kotlinx.coroutines.Job;

/** sherpa JNI looks up invoke(float[]) returning java.lang.Integer by its exact signature. */
public final class SpeechGenerationCallback implements Function1<float[], Integer> {
    private final Job job;
    public SpeechGenerationCallback(Job job) { this.job = job; }
    @Override public Integer invoke(float[] samples) { return job == null || job.isActive() ? 1 : 0; }
}

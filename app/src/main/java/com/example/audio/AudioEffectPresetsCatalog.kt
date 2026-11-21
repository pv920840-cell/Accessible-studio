package com.example.audio

data class AudioEffectPresetItem(
    val id: String,
    val name: String,
    val targetCategory: String,
    val intensityTier: String,
    val parameterSummary: String,
    val accessibilityDescription: String,
    val applyTransform: (pcm: ShortArray, wavProcessor: WavAudioProcessor, trimStartMs: Long, trimEndMs: Long) -> ShortArray
)

data class EffectPresetGroup(
    val effectId: String,
    val effectName: String,
    val presets: List<AudioEffectPresetItem>
)

object AudioEffectPresetsCatalog {

    val groups: List<EffectPresetGroup> = listOf(
        EffectPresetGroup(
            effectId = "equalizer",
            effectName = "1. Equalizer (EQ)",
            presets = listOf(
                AudioEffectPresetItem(
                    id = "eq_podcast_clarity_standard",
                    name = "Podcast Voice Clarity",
                    targetCategory = "Podcast / Spoken Word",
                    intensityTier = "Standard",
                    parameterSummary = "HPF 85Hz • -2.5dB @400Hz • +3dB @2.5kHz",
                    accessibilityDescription = "Podcast Voice Clarity. Removes low rumble below 85 Hertz, reduces boxy mud around 400 Hertz, and boosts speech intelligibility in the upper midrange.",
                    applyTransform = { pcm, _, _, _ ->
                        val hp = StudioAudioProcessor.applyHighPassFilter(pcm, 85f)
                        StudioAudioProcessor.apply7BandEq(hp, -4f, -1.5f, -2.5f, 0.5f, 3.0f, 2.0f, 1.0f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "eq_male_vocal_warmth",
                    name = "Male Vocal Chest & Presence",
                    targetCategory = "Male Vocal",
                    intensityTier = "Standard",
                    parameterSummary = "HPF 90Hz • +2dB @150Hz • +3dB @6kHz",
                    accessibilityDescription = "Male Vocal Chest and Presence. Enhances natural low-end warmth at 150 Hertz while lifting vocal articulation and air.",
                    applyTransform = { pcm, _, _, _ ->
                        val hp = StudioAudioProcessor.applyHighPassFilter(pcm, 90f)
                        StudioAudioProcessor.apply7BandEq(hp, -3f, 2.0f, -2.0f, 0f, 2.5f, 3.0f, 1.5f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "eq_female_vocal_air",
                    name = "Female Vocal Silky Air",
                    targetCategory = "Female Vocal",
                    intensityTier = "Subtle",
                    parameterSummary = "HPF 115Hz • +2dB @2.5kHz • +3.5dB @15kHz",
                    accessibilityDescription = "Female Vocal Silky Air. Gently cuts sub-bass rumble and adds bright, open treble shimmer above 10 kilohertz without harshness.",
                    applyTransform = { pcm, _, _, _ ->
                        val hp = StudioAudioProcessor.applyHighPassFilter(pcm, 115f)
                        StudioAudioProcessor.apply7BandEq(hp, -6f, -1f, -1.5f, 1.0f, 2.0f, 1.5f, 3.5f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "eq_acoustic_guitar_sparkle",
                    name = "Acoustic Guitar Balance",
                    targetCategory = "Instrumental",
                    intensityTier = "Standard",
                    parameterSummary = "HPF 80Hz • -3.5dB @400Hz • +3.5dB @6kHz",
                    accessibilityDescription = "Acoustic Guitar Balance. Scoops hollow body resonance near 400 Hertz and highlights crisp pick detail and string shimmer.",
                    applyTransform = { pcm, _, _, _ ->
                        val hp = StudioAudioProcessor.applyHighPassFilter(pcm, 80f)
                        StudioAudioProcessor.apply7BandEq(hp, -3f, 1.5f, -3.5f, 0f, 2.0f, 3.5f, 2.5f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "eq_telephone_radio_fx",
                    name = "Vintage AM Radio / Telephone",
                    targetCategory = "Sound Design",
                    intensityTier = "Aggressive",
                    parameterSummary = "Bandpass 420Hz–3.2kHz • +5dB @1kHz",
                    accessibilityDescription = "Vintage AM Radio and Telephone. Narrow bandpass effect cutting all deep bass and high treble to simulate a classic handheld receiver.",
                    applyTransform = { pcm, _, _, _ ->
                        val bp = StudioAudioProcessor.applyBandPassFilter(pcm, 1350f, 1800f)
                        StudioAudioProcessor.apply7BandEq(bp, -12f, -12f, -2f, 5f, 4f, -10f, -12f)
                    }
                )
            )
        ),
        EffectPresetGroup(
            effectId = "compressor_limiter",
            effectName = "2. Compressor & Limiter",
            presets = listOf(
                AudioEffectPresetItem(
                    id = "comp_gentle_vocal_leveler",
                    name = "Gentle Vocal Leveler",
                    targetCategory = "Male Vocal / Female Vocal",
                    intensityTier = "Subtle",
                    parameterSummary = "Thresh -16dB • Ratio 2.2:1 • +2dB Makeup",
                    accessibilityDescription = "Gentle Vocal Leveler. Transparently evens out quiet and loud syllables with a soft 2.2 to 1 ratio and natural attack.",
                    applyTransform = { pcm, _, _, _ ->
                        StudioAudioProcessor.applyCompressor(pcm, thresholdDb = -16f, ratio = 2.2f, makeupGainDb = 2.0f, peakLimiter = true)
                    }
                ),
                AudioEffectPresetItem(
                    id = "comp_podcast_broadcast",
                    name = "Broadcast Spoken Word",
                    targetCategory = "Podcast / Narration",
                    intensityTier = "Standard",
                    parameterSummary = "Thresh -18.5dB • Ratio 4:1 • +4.5dB Makeup",
                    accessibilityDescription = "Broadcast Spoken Word. Locks narration volume at a consistent, radio-ready level with a 4 to 1 ratio and minus 1 decibel safety ceiling.",
                    applyTransform = { pcm, _, _, _ ->
                        val comp = StudioAudioProcessor.applyCompressor(pcm, thresholdDb = -18.5f, ratio = 4.0f, makeupGainDb = 4.5f, peakLimiter = true)
                        StudioAudioProcessor.applyTruePeakLimiter(comp, -1.0f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "comp_aggressive_pop_vocal",
                    name = "Modern Pop Vocal Slam",
                    targetCategory = "Vocal / Music Production",
                    intensityTier = "Aggressive",
                    parameterSummary = "Thresh -22dB • Ratio 6.5:1 • +6.5dB Makeup",
                    accessibilityDescription = "Modern Pop Vocal Slam. Fast compression keeps lead vocals upfront and dense over loud music.",
                    applyTransform = { pcm, _, _, _ ->
                        val comp = StudioAudioProcessor.applyCompressor(pcm, thresholdDb = -22f, ratio = 6.5f, makeupGainDb = 6.5f, peakLimiter = true)
                        StudioAudioProcessor.applyTruePeakLimiter(comp, -0.5f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "comp_parallel_drum_punch",
                    name = "Parallel Drum Bus Punch",
                    targetCategory = "Instrumental / Drums",
                    intensityTier = "Specialized",
                    parameterSummary = "Thresh -26dB • Ratio 8:1 • 40% Parallel Mix",
                    accessibilityDescription = "Parallel Drum Bus Punch. Heavy compression blended at 40 percent wet mix to add thickness and sustain while preserving drum snap.",
                    applyTransform = { pcm, _, _, _ ->
                        val crushed = StudioAudioProcessor.applyCompressor(pcm, thresholdDb = -26f, ratio = 8.0f, makeupGainDb = 7.0f, peakLimiter = true)
                        ShortArray(pcm.size) { i ->
                            (pcm[i] * 0.60f + crushed[i] * 0.40f).toInt().coerceIn(-32767, 32767).toShort()
                        }
                    }
                ),
                AudioEffectPresetItem(
                    id = "comp_master_bus_glue",
                    name = "Master Bus Glue & Safety Limiter",
                    targetCategory = "Mastering / Full Mix",
                    intensityTier = "Subtle",
                    parameterSummary = "Thresh -12dB • Ratio 1.8:1 • Ceiling -1.0dBFS",
                    accessibilityDescription = "Master Bus Glue and Safety Limiter. Gently binds instruments together and protects against digital clipping at minus 1 decibel.",
                    applyTransform = { pcm, _, _, _ ->
                        val comp = StudioAudioProcessor.applyCompressor(pcm, thresholdDb = -12f, ratio = 1.8f, makeupGainDb = 1.5f, peakLimiter = true)
                        StudioAudioProcessor.applyTruePeakLimiter(comp, -1.0f)
                    }
                )
            )
        ),
        EffectPresetGroup(
            effectId = "noise_gate_reduction",
            effectName = "3. Noise Gate & Reduction",
            presets = listOf(
                AudioEffectPresetItem(
                    id = "vocal_iso_light",
                    name = "Isolate Speech: Light Filtering",
                    targetCategory = "Spoken Audio / Natural Room",
                    intensityTier = "Subtle",
                    parameterSummary = "Adaptive Spectral • Light • Clarity +1.2dB",
                    accessibilityDescription = "Isolate Speech Light Filtering. Gentle adaptive spectral subtraction and rumble cut while keeping natural room warmth and vocal air.",
                    applyTransform = { pcm, _, _, _ ->
                        VocalIsolationProcessor.isolateSpokenAudio(pcm, VocalIsolationPreset.LIGHT)
                    }
                ),
                AudioEffectPresetItem(
                    id = "vocal_iso_medium",
                    name = "Isolate Speech: Medium Filtering",
                    targetCategory = "Spoken Audio / Podcast & Voice",
                    intensityTier = "Standard",
                    parameterSummary = "Adaptive Spectral • Medium • De-Hum + Clarity",
                    accessibilityDescription = "Isolate Speech Medium Filtering. Removes ambient noise and 50 or 60 hertz hum using adaptive spectral subtraction with crisp vocal clarity.",
                    applyTransform = { pcm, _, _, _ ->
                        VocalIsolationProcessor.isolateSpokenAudio(pcm, VocalIsolationPreset.MEDIUM)
                    }
                ),
                AudioEffectPresetItem(
                    id = "nr_studio_fan_motor_ultra",
                    name = "Studio Ultra Clean: Fan, Motor & All Noise",
                    targetCategory = "Studio Clean / Fan & Motor Removal",
                    intensityTier = "Aggressive",
                    parameterSummary = "2048 STFT • Motor Comb Hunter • Studio Clarity",
                    accessibilityDescription = "Studio Ultra Clean Fan and Motor Remover. Completely removes ceiling fans, table fans, motors, air conditioning rumble, hum, and hiss offline with broadcast studio voice clarity.",
                    applyTransform = { pcm, _, _, _ ->
                        VocalIsolationProcessor.isolateSpokenAudio(pcm, VocalIsolationPreset.STUDIO_DEEP_CLEAN)
                    }
                ),
                AudioEffectPresetItem(
                    id = "vocal_iso_strong",
                    name = "Isolate Speech: Strong Filtering",
                    targetCategory = "Spoken Audio / Heavy Ambient Rescue",
                    intensityTier = "Aggressive",
                    parameterSummary = "Adaptive Spectral • Strong • Full Isolation",
                    accessibilityDescription = "Isolate Speech Strong Filtering. Maximum spoken audio isolation removing heavy ambient noise and electrical hum while preserving pristine vocal clarity.",
                    applyTransform = { pcm, _, _, _ ->
                        VocalIsolationProcessor.isolateSpokenAudio(pcm, VocalIsolationPreset.STRONG)
                    }
                ),
                AudioEffectPresetItem(
                    id = "nr_transparent_studio_clean",
                    name = "Transparent Studio De-Hiss",
                    targetCategory = "Vocal / Acoustic",
                    intensityTier = "Subtle",
                    parameterSummary = "Spectral 35% • Gate -54dB",
                    accessibilityDescription = "Transparent Studio De-Hiss. Light 35 percent spectral hiss removal that preserves natural room breath and high-frequency detail.",
                    applyTransform = { pcm, wav, _, _ ->
                        val nr = wav.applyNoiseReduction(pcm, 0.35f)
                        wav.applyNoiseGate(nr, 0.004f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "nr_podcast_room_clean",
                    name = "Podcast Fan & Room Cleaner",
                    targetCategory = "Podcast / Voiceover",
                    intensityTier = "Standard",
                    parameterSummary = "Spectral 60% • Gate -38dB",
                    accessibilityDescription = "Podcast Fan and Room Cleaner. Removes computer fan and air conditioning hum at 60 percent strength while gating pauses cleanly.",
                    applyTransform = { pcm, wav, _, _ ->
                        val nr = wav.applyNoiseReduction(pcm, 0.60f)
                        wav.applyNoiseGate(nr, 0.02f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "nr_heavy_field_rescue",
                    name = "Heavy Field Recording Rescue",
                    targetCategory = "Field Recording / Interview",
                    intensityTier = "Aggressive",
                    parameterSummary = "Spectral 85% • Gate -30dB",
                    accessibilityDescription = "Heavy Field Recording Rescue. Strong 85 percent noise suppression and tighter gate to rescue speech recorded outdoors or in noisy rooms.",
                    applyTransform = { pcm, wav, _, _ ->
                        val nr = wav.applyNoiseReduction(pcm, 0.85f)
                        wav.applyNoiseGate(nr, 0.045f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "nr_tight_percussion_gate",
                    name = "Tight Drum & Perc Gate",
                    targetCategory = "Instrumental / Drums",
                    intensityTier = "Specialized",
                    parameterSummary = "Gate -22dB • Spectral 15%",
                    accessibilityDescription = "Tight Drum and Percussion Gate. High-threshold gating mutes mic bleed cleanly between drum hits.",
                    applyTransform = { pcm, wav, _, _ ->
                        val nr = wav.applyNoiseReduction(pcm, 0.15f)
                        wav.applyNoiseGate(nr, 0.09f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "nr_breath_and_pause_silencer",
                    name = "Narration Breath & Pause Gate",
                    targetCategory = "Audiobook / Accessibility Narration",
                    intensityTier = "Specialized",
                    parameterSummary = "Spectral 50% • Gate -34dB",
                    accessibilityDescription = "Narration Breath and Pause Gate. Smoothly reduces background noise and quiet breaths between spoken sentences.",
                    applyTransform = { pcm, wav, _, _ ->
                        val nr = wav.applyNoiseReduction(pcm, 0.50f)
                        wav.applyNoiseGate(nr, 0.03f)
                    }
                )
            )
        ),
        EffectPresetGroup(
            effectId = "reverb",
            effectName = "4. Reverb & Room",
            presets = listOf(
                AudioEffectPresetItem(
                    id = "rev_intimate_vocal_booth",
                    name = "Intimate Vocal Booth",
                    targetCategory = "Podcast / Voiceover",
                    intensityTier = "Subtle",
                    parameterSummary = "Room 22% • Damp 65% • Mix 12%",
                    accessibilityDescription = "Intimate Vocal Booth. Adds subtle natural room air so dry studio speech sounds realistic without echoing.",
                    applyTransform = { pcm, wav, _, _ -> wav.applyReverb(pcm, roomSize = 0.22f, damping = 0.65f, mix = 0.14f) }
                ),
                AudioEffectPresetItem(
                    id = "rev_warm_studio_plate",
                    name = "Warm Vocal Plate",
                    targetCategory = "Male Vocal / Female Vocal",
                    intensityTier = "Standard",
                    parameterSummary = "Room 55% • Damp 42% • Mix 24%",
                    accessibilityDescription = "Warm Vocal Plate. Classic smooth studio plate tail keeping lead lyrics crisp and musical.",
                    applyTransform = { pcm, wav, _, _ -> wav.applyReverb(pcm, roomSize = 0.55f, damping = 0.42f, mix = 0.24f) }
                ),
                AudioEffectPresetItem(
                    id = "rev_concert_hall",
                    name = "Grand Concert Hall",
                    targetCategory = "Instrumental / Orchestral",
                    intensityTier = "Standard",
                    parameterSummary = "Room 80% • Damp 38% • Mix 32%",
                    accessibilityDescription = "Grand Concert Hall. Lush acoustic hall simulation designed for piano, strings, and acoustic ensembles.",
                    applyTransform = { pcm, wav, _, _ -> wav.applyReverb(pcm, roomSize = 0.80f, damping = 0.38f, mix = 0.32f) }
                ),
                AudioEffectPresetItem(
                    id = "rev_cathedral_shimmer",
                    name = "Ethereal Cathedral Ambient",
                    targetCategory = "Sound Design / Ambient",
                    intensityTier = "Aggressive",
                    parameterSummary = "Room 96% • Damp 20% • Mix 55%",
                    accessibilityDescription = "Ethereal Cathedral Ambient. Massive bright reverberation washing audio into an atmospheric soundscape.",
                    applyTransform = { pcm, wav, _, _ -> wav.applyReverb(pcm, roomSize = 0.96f, damping = 0.20f, mix = 0.55f) }
                ),
                AudioEffectPresetItem(
                    id = "rev_tight_drum_room",
                    name = "Live Wood Drum Room",
                    targetCategory = "Instrumental / Drums",
                    intensityTier = "Specialized",
                    parameterSummary = "Room 38% • Damp 50% • Mix 20%",
                    accessibilityDescription = "Live Wood Drum Room. Tight, punchy early reflections giving dry percussion physical depth.",
                    applyTransform = { pcm, wav, _, _ -> wav.applyReverb(pcm, roomSize = 0.38f, damping = 0.50f, mix = 0.20f) }
                )
            )
        ),
        EffectPresetGroup(
            effectId = "delay_echo",
            effectName = "5. Delay & Echo",
            presets = listOf(
                AudioEffectPresetItem(
                    id = "dly_vocal_slapback",
                    name = "Classic Vocal Slapback",
                    targetCategory = "Male Vocal / Acoustic",
                    intensityTier = "Subtle",
                    parameterSummary = "105ms • Feedback 12% • Mix 22%",
                    accessibilityDescription = "Classic Vocal Slapback. Single tight 105 millisecond repeat thickens voices without cluttering phrasing.",
                    applyTransform = { pcm, wav, _, _ -> wav.applyEcho(pcm, delayMs = 105L, feedback = 0.12f, mix = 0.22f) }
                ),
                AudioEffectPresetItem(
                    id = "dly_clean_quarter_note",
                    name = "Studio Quarter-Note Echo",
                    targetCategory = "Vocal / Instrumental",
                    intensityTier = "Standard",
                    parameterSummary = "500ms • Feedback 38% • Mix 28%",
                    accessibilityDescription = "Studio Quarter-Note Echo. Half-second rhythmic repeat fading naturally over 3 to 4 gentle echoes.",
                    applyTransform = { pcm, wav, _, _ -> wav.applyEcho(pcm, delayMs = 500L, feedback = 0.38f, mix = 0.28f) }
                ),
                AudioEffectPresetItem(
                    id = "dly_pingpong_wide",
                    name = "Dotted Eighth Groove Delay",
                    targetCategory = "Electronic / Synth / Guitar",
                    intensityTier = "Standard",
                    parameterSummary = "375ms • Feedback 48% • Mix 35%",
                    accessibilityDescription = "Dotted Eighth Groove Delay. Rhythmic 375 millisecond echoes adding lively bounce to guitars and synths.",
                    applyTransform = { pcm, wav, _, _ -> wav.applyEcho(pcm, delayMs = 375L, feedback = 0.48f, mix = 0.35f) }
                ),
                AudioEffectPresetItem(
                    id = "dly_dub_tape_space",
                    name = "Dub Tape Space Cascade",
                    targetCategory = "Sound Design / Dub",
                    intensityTier = "Aggressive",
                    parameterSummary = "420ms • Feedback 72% • Mix 46%",
                    accessibilityDescription = "Dub Tape Space Cascade. Long, warm filtered repeats with 72 percent feedback creating hypnotic trailing echoes.",
                    applyTransform = { pcm, wav, _, _ ->
                        val warm = StudioAudioProcessor.applyLowPassFilter(pcm, 3200f)
                        wav.applyEcho(warm, delayMs = 420L, feedback = 0.72f, mix = 0.46f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "dly_haas_stereo_doubler",
                    name = "Micro Vocal Thickener",
                    targetCategory = "Vocal / Lead Doubling",
                    intensityTier = "Specialized",
                    parameterSummary = "28ms • Feedback 0% • Mix 30%",
                    accessibilityDescription = "Micro Vocal Thickener. Ultra-short 28 millisecond single delay mimics a double-tracked vocal performance.",
                    applyTransform = { pcm, wav, _, _ -> wav.applyEcho(pcm, delayMs = 28L, feedback = 0.0f, mix = 0.30f) }
                )
            )
        ),
        EffectPresetGroup(
            effectId = "chorus_flanger_phaser",
            effectName = "6. Chorus, Flanger & Phaser",
            presets = listOf(
                AudioEffectPresetItem(
                    id = "mod_subtle_acoustic_chorus",
                    name = "Silky Acoustic Ensemble Chorus",
                    targetCategory = "Instrumental / Keys / Guitar",
                    intensityTier = "Subtle",
                    parameterSummary = "Chorus • 0.85Hz • 5.5ms • Mix 32%",
                    accessibilityDescription = "Silky Acoustic Ensemble Chorus. Slow gentle pitch shimmer adds lush width to clean guitars, pianos, and backing vocals.",
                    applyTransform = { pcm, _, _, _ -> StudioAudioProcessor.applyChorus(pcm, rateHz = 0.85f, depthMs = 5.5f, mix = 0.32f) }
                ),
                AudioEffectPresetItem(
                    id = "mod_rich_vocal_doubler",
                    name = "Lush Vocal Detune Chorus",
                    targetCategory = "Vocal / Harmony",
                    intensityTier = "Standard",
                    parameterSummary = "Chorus • 1.35Hz • 8.0ms • Mix 45%",
                    accessibilityDescription = "Lush Vocal Detune Chorus. Medium modulation thickens solo singing to sound like layered voices.",
                    applyTransform = { pcm, _, _, _ -> StudioAudioProcessor.applyChorus(pcm, rateHz = 1.35f, depthMs = 8.0f, mix = 0.45f) }
                ),
                AudioEffectPresetItem(
                    id = "mod_classic_jet_flanger",
                    name = "Jet Engine Sweep Flanger",
                    targetCategory = "Sound Design / Electric Guitar",
                    intensityTier = "Aggressive",
                    parameterSummary = "Flanger • 0.35Hz • 4.2ms • FB 68% • Mix 55%",
                    accessibilityDescription = "Jet Engine Sweep Flanger. Slow deep comb-filter sweep with high feedback creates dramatic whooshing motion.",
                    applyTransform = { pcm, _, _, _ -> StudioAudioProcessor.applyFlanger(pcm, rateHz = 0.35f, depthMs = 4.2f, feedback = 0.68f, mix = 0.55f) }
                ),
                AudioEffectPresetItem(
                    id = "mod_subtle_tape_flange",
                    name = "Warm Tape Motion Flange",
                    targetCategory = "Instrumental / Synth",
                    intensityTier = "Standard",
                    parameterSummary = "Flanger • 0.5Hz • 2.8ms • FB 35% • Mix 38%",
                    accessibilityDescription = "Warm Tape Motion Flange. Balanced sweeping movement inspired by classic analog reel-to-reel tape flanging.",
                    applyTransform = { pcm, _, _, _ -> StudioAudioProcessor.applyFlanger(pcm, rateHz = 0.5f, depthMs = 2.8f, feedback = 0.35f, mix = 0.38f) }
                ),
                AudioEffectPresetItem(
                    id = "mod_psychedelic_4stage_phaser",
                    name = "Liquid Sweep Phaser",
                    targetCategory = "Sound Design / Keys / Bass",
                    intensityTier = "Specialized",
                    parameterSummary = "Sweep • 0.65Hz • 9.5ms • FB 52% • Mix 50%",
                    accessibilityDescription = "Liquid Sweep Phaser. Smooth swirling phase notches ideal for electric pianos, pads, and sci-fi sound effects.",
                    applyTransform = { pcm, _, _, _ -> StudioAudioProcessor.applyFlanger(pcm, rateHz = 0.65f, depthMs = 9.5f, feedback = 0.52f, mix = 0.50f) }
                )
            )
        ),
        EffectPresetGroup(
            effectId = "pitch_formant_shift",
            effectName = "7. Pitch & Formant Shift",
            presets = listOf(
                AudioEffectPresetItem(
                    id = "pitch_micro_detune_wide",
                    name = "Micro Harmony Thickener",
                    targetCategory = "Vocal / Lead & Backing",
                    intensityTier = "Subtle",
                    parameterSummary = "+1 Semitone Blended 30%",
                    accessibilityDescription = "Micro Harmony Thickener. Blends a subtle shifted harmony layer underneath the dry voice for studio width.",
                    applyTransform = { pcm, _, _, _ ->
                        val shifted = StudioAudioProcessor.applyPitchShift(pcm, 1)
                        val minLen = minOf(pcm.size, shifted.size)
                        ShortArray(pcm.size) { i ->
                            if (i < minLen) (pcm[i] * 0.72f + shifted[i] * 0.28f).toInt().coerceIn(-32767, 32767).toShort()
                            else pcm[i]
                        }
                    }
                ),
                AudioEffectPresetItem(
                    id = "pitch_octave_up_harmony",
                    name = "Clean +5 Fifth Harmony",
                    targetCategory = "Vocal / Instrumental",
                    intensityTier = "Standard",
                    parameterSummary = "+7 Semitones • Blended 35%",
                    accessibilityDescription = "Clean Fifth Harmony. Layers a fifth harmony up 7 semitones above the original performance.",
                    applyTransform = { pcm, _, _, _ ->
                        val shifted = StudioAudioProcessor.applyPitchShift(pcm, 7)
                        val minLen = minOf(pcm.size, shifted.size)
                        ShortArray(pcm.size) { i ->
                            if (i < minLen) (pcm[i] * 0.65f + shifted[i] * 0.35f).toInt().coerceIn(-32767, 32767).toShort()
                            else pcm[i]
                        }
                    }
                ),
                AudioEffectPresetItem(
                    id = "pitch_deep_anonymous_voice",
                    name = "Deep Cinema / Anonymous Voice",
                    targetCategory = "Sound Design / Narration",
                    intensityTier = "Aggressive",
                    parameterSummary = "-5 Semitones • 100% Wet",
                    accessibilityDescription = "Deep Cinema and Anonymous Voice. Drops pitch 5 semitones for dramatic trailers or voice privacy masking.",
                    applyTransform = { pcm, _, _, _ -> StudioAudioProcessor.applyPitchShift(pcm, -5) }
                ),
                AudioEffectPresetItem(
                    id = "pitch_sub_octave_layer",
                    name = "Sub-Octave Bass Layer",
                    targetCategory = "Instrumental / Bass / Vocal",
                    intensityTier = "Specialized",
                    parameterSummary = "-12 Semitones • 38% Wet",
                    accessibilityDescription = "Sub-Octave Bass Layer. Mixes a full octave-lower shadow at 38 percent beneath the original sound for heavy weight.",
                    applyTransform = { pcm, _, _, _ ->
                        val sub = StudioAudioProcessor.applyPitchShift(pcm, -12)
                        val minLen = minOf(pcm.size, sub.size)
                        ShortArray(pcm.size) { i ->
                            if (i < minLen) (pcm[i] * 0.65f + sub[i] * 0.38f).toInt().coerceIn(-32767, 32767).toShort()
                            else pcm[i]
                        }
                    }
                ),
                AudioEffectPresetItem(
                    id = "pitch_bright_formant_lift",
                    name = "Bright Character Voice Lift",
                    targetCategory = "Character Voice / Sound Design",
                    intensityTier = "Specialized",
                    parameterSummary = "+3 Semitones • 100% Wet",
                    accessibilityDescription = "Bright Character Voice Lift. Shifts pitch upward by 3 semitones for expressive animated character voices.",
                    applyTransform = { pcm, _, _, _ -> StudioAudioProcessor.applyPitchShift(pcm, 3) }
                )
            )
        ),
        EffectPresetGroup(
            effectId = "distortion_saturation",
            effectName = "8. Distortion & Saturation",
            presets = listOf(
                AudioEffectPresetItem(
                    id = "sat_analog_tape_warmth",
                    name = "Warm Analog Tape Saturation",
                    targetCategory = "Podcast / Vocal / Master",
                    intensityTier = "Subtle",
                    parameterSummary = "Drive 1.4x • Tone 82% • Mix 45%",
                    accessibilityDescription = "Warm Analog Tape Saturation. Gently rounds harsh digital peaks and adds subtle harmonic richness to speech and mixes.",
                    applyTransform = { pcm, _, _, _ ->
                        val sat = StudioAudioProcessor.applyDistortion(pcm, drive = 1.4f, tone = 0.82f)
                        ShortArray(pcm.size) { i -> (pcm[i] * 0.55f + sat[i] * 0.45f).toInt().coerceIn(-32767, 32767).toShort() }
                    }
                ),
                AudioEffectPresetItem(
                    id = "sat_vintage_tube_grit",
                    name = "Vintage Tube Console Grit",
                    targetCategory = "Male Vocal / Bass",
                    intensityTier = "Standard",
                    parameterSummary = "Drive 2.6x • Tone 68% • Mix 65%",
                    accessibilityDescription = "Vintage Tube Console Grit. Adds medium harmonic crunch and punch to help bass and rock vocals cut through.",
                    applyTransform = { pcm, _, _, _ ->
                        val sat = StudioAudioProcessor.applyDistortion(pcm, drive = 2.6f, tone = 0.68f)
                        ShortArray(pcm.size) { i -> (pcm[i] * 0.35f + sat[i] * 0.65f).toInt().coerceIn(-32767, 32767).toShort() }
                    }
                ),
                AudioEffectPresetItem(
                    id = "sat_crunchy_amp_overdrive",
                    name = "Crunchy Blues Amp Overdrive",
                    targetCategory = "Instrumental / Guitar",
                    intensityTier = "Standard",
                    parameterSummary = "Drive 4.8x • Tone 58% • 100% Wet",
                    accessibilityDescription = "Crunchy Blues Amp Overdrive. Warm, expressive guitar distortion with balanced midrange bite.",
                    applyTransform = { pcm, _, _, _ -> StudioAudioProcessor.applyDistortion(pcm, drive = 4.8f, tone = 0.58f) }
                ),
                AudioEffectPresetItem(
                    id = "sat_heavy_fuzz_wall",
                    name = "Heavy Fuzz Lead & Industrial",
                    targetCategory = "Sound Design / Heavy Guitar",
                    intensityTier = "Aggressive",
                    parameterSummary = "Drive 9.5x • Tone 45% • 100% Wet",
                    accessibilityDescription = "Heavy Fuzz Lead and Industrial. Extreme high-gain saturation transforming audio into a thick, buzzing wall of distortion.",
                    applyTransform = { pcm, _, _, _ -> StudioAudioProcessor.applyDistortion(pcm, drive = 9.5f, tone = 0.45f) }
                ),
                AudioEffectPresetItem(
                    id = "sat_lofi_megaphone_crunch",
                    name = "Lo-Fi Megaphone Crunch",
                    targetCategory = "Sound Design / Effects",
                    intensityTier = "Specialized",
                    parameterSummary = "Drive 6.2x • Tone 32% • Bandpass",
                    accessibilityDescription = "Lo-Fi Megaphone Crunch. Gritty, edgy overdrive tuned for bullhorn callouts and distorted radio transmissions.",
                    applyTransform = { pcm, _, _, _ ->
                        val bp = StudioAudioProcessor.applyBandPassFilter(pcm, 1500f, 1900f)
                        StudioAudioProcessor.applyDistortion(bp, drive = 6.2f, tone = 0.32f)
                    }
                )
            )
        ),
        EffectPresetGroup(
            effectId = "normalizer_maximizer",
            effectName = "9. Normalizer & Maximizer",
            presets = listOf(
                AudioEffectPresetItem(
                    id = "norm_safe_headroom_peak",
                    name = "Safe Editing Peak (-3.0 dBFS)",
                    targetCategory = "General Editing / Pre-Mix",
                    intensityTier = "Subtle",
                    parameterSummary = "Peak Ceiling -3.0 dBFS (0.71 ratio)",
                    accessibilityDescription = "Safe Editing Peak at minus 3 decibels. Brings quiet recordings up cleanly while leaving generous headroom for extra effects.",
                    applyTransform = { pcm, _, _, _ -> StudioAudioProcessor.normalize(pcm, targetRatio = 0.708f) }
                ),
                AudioEffectPresetItem(
                    id = "norm_podcast_lufs_standard",
                    name = "Podcast & Audiobook Standard (-16 dBFS)",
                    targetCategory = "Podcast / Narration",
                    intensityTier = "Standard",
                    parameterSummary = "RMS -16 dBFS • Limiter -1.0 dBFS",
                    accessibilityDescription = "Podcast and Audiobook Standard. Targets minus 16 decibels RMS loudness with a minus 1 decibel peak ceiling for mobile playback.",
                    applyTransform = { pcm, _, _, _ ->
                        val rms = StudioAudioProcessor.normalizeRms(pcm, -16.0f)
                        StudioAudioProcessor.applyTruePeakLimiter(rms, -1.0f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "norm_streaming_music_master",
                    name = "Streaming Music Target (-14 dBFS)",
                    targetCategory = "Music / Mastering",
                    intensityTier = "Standard",
                    parameterSummary = "RMS -14 dBFS • Limiter -1.0 dBFS",
                    accessibilityDescription = "Streaming Music Target. Matches minus 14 decibels streaming playback levels without squashing musical dynamics.",
                    applyTransform = { pcm, _, _, _ ->
                        val rms = StudioAudioProcessor.normalizeRms(pcm, -14.0f)
                        StudioAudioProcessor.applyTruePeakLimiter(rms, -1.0f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "norm_maximum_commercial_loudness",
                    name = "Maximum Loudness Maximizer (-9 dBFS)",
                    targetCategory = "Commercial / Electronic Music",
                    intensityTier = "Aggressive",
                    parameterSummary = "Loudness +6dB • RMS -9.5 dBFS • Limiter -0.2dB",
                    accessibilityDescription = "Maximum Loudness Maximizer. Pushes overall volume to competitive commercial loudness with brickwall limiting at minus 0.2 decibels.",
                    applyTransform = { pcm, _, _, _ ->
                        val contour = AudioEffectsProEngine.applyLoudnessContour(pcm, 5.0f)
                        val rms = StudioAudioProcessor.normalizeRms(contour, -9.5f)
                        StudioAudioProcessor.applyTruePeakLimiter(rms, -0.2f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "norm_broadcast_ebu_r128",
                    name = "Broadcast TV & Cinema Safe (-23 dBFS)",
                    targetCategory = "Broadcast / Video Soundtrack",
                    intensityTier = "Specialized",
                    parameterSummary = "RMS -23 dBFS • Limiter -1.0 dBFS",
                    accessibilityDescription = "Broadcast TV and Cinema Safe. Calibrates audio to minus 23 decibels broadcast standards with natural dynamic range.",
                    applyTransform = { pcm, _, _, _ ->
                        val rms = StudioAudioProcessor.normalizeRms(pcm, -23.0f)
                        StudioAudioProcessor.applyTruePeakLimiter(rms, -1.0f)
                    }
                )
            )
        ),
        EffectPresetGroup(
            effectId = "stereo_panning_imager",
            effectName = "10. Stereo Panning & Imager",
            presets = listOf(
                AudioEffectPresetItem(
                    id = "pan_true_mono_center",
                    name = "True Mono Center Focus",
                    targetCategory = "Podcast / Voiceover / Accessibility",
                    intensityTier = "Specialized",
                    parameterSummary = "Pan 0% • Width 0% (Mono Focus)",
                    accessibilityDescription = "True Mono Center Focus. Collapses stereo phase issues to dead center so spoken voice stays equally loud in both ears.",
                    applyTransform = { pcm, _, _, _ ->
                        AudioEffectsProEngine.applyPanAndStereoCenter(pcm, panValue = 0.0f, centerWidth = 0.0f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "pan_subtle_acoustic_width",
                    name = "Natural Studio Width (120%)",
                    targetCategory = "Instrumental / Full Mix",
                    intensityTier = "Subtle",
                    parameterSummary = "Pan 0% • Center Width 0.65",
                    accessibilityDescription = "Natural Studio Width. Gently expands stereo warmth while keeping center bass and vocals solid.",
                    applyTransform = { pcm, _, _, _ ->
                        AudioEffectsProEngine.applyPanAndStereoCenter(pcm, panValue = 0.0f, centerWidth = 0.65f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "pan_wide_cinematic_horizon",
                    name = "Ultra-Wide Cinematic Spread",
                    targetCategory = "Sound Design / Pads / Backing Tracks",
                    intensityTier = "Aggressive",
                    parameterSummary = "Pan 0% • Extra-Wide Horizon 1.0",
                    accessibilityDescription = "Ultra-Wide Cinematic Spread. Pushes ambient textures and pads wide for an enveloping headphone image.",
                    applyTransform = { pcm, _, _, _ ->
                        AudioEffectsProEngine.applyPanAndStereoCenter(pcm, panValue = 0.0f, centerWidth = 1.0f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "pan_rhythm_left_balance",
                    name = "Rhythm Left Pocket (-35%)",
                    targetCategory = "Instrumental / Multi-Track Mixing",
                    intensityTier = "Standard",
                    parameterSummary = "Pan -35% Left • Center 0.40",
                    accessibilityDescription = "Rhythm Instrument Left Pocket. Positions the track 35 percent toward the left ear to clear space in the center for lead speech.",
                    applyTransform = { pcm, _, _, _ ->
                        AudioEffectsProEngine.applyPanAndStereoCenter(pcm, panValue = -0.35f, centerWidth = 0.40f)
                    }
                ),
                AudioEffectPresetItem(
                    id = "pan_harmony_right_balance",
                    name = "Counter-Harmony Right Pocket (+35%)",
                    targetCategory = "Vocal Harmony / Multi-Track Mixing",
                    intensityTier = "Standard",
                    parameterSummary = "Pan +35% Right • Center 0.40",
                    accessibilityDescription = "Counter-Harmony Right Pocket. Positions backing layers 35 percent toward the right ear for balanced stereo separation.",
                    applyTransform = { pcm, _, _, _ ->
                        AudioEffectsProEngine.applyPanAndStereoCenter(pcm, panValue = 0.35f, centerWidth = 0.40f)
                    }
                )
            )
        )
    )
}

package com.pockettavern.app.ui.screens.help

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettavern.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGuideScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Introduction ────────────────────────────────────────
            Text(text = stringResource(R.string.welcome_to_pockettavern),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = stringResource(R.string.pockettavern_is_a_standalone_ai_character_cha),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.pockettavern_does_not_host_or_provide_any_ai),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Getting Started ─────────────────────────────────────
            HelpDropdown(title = "Getting Started") {
                SectionHeading(stringResource(R.string.step_1_get_some_characters))
                BulletItem(stringResource(R.string.import_a_png_card_go_to_characters_tap_the_im))
                BulletItem(stringResource(R.string.create_a_character_tap_create_character_on_th))
                BulletItem(stringResource(R.string.browse_charavault_tap_charavault_on_the_home))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.step_2_connect_an_llm_backend))
                BulletItem(stringResource(R.string.open_settings_api_configuration_select_your_a))
                BulletItem(stringResource(R.string.local_backends_koboldcpp_ollama_lm_studio_etc))
                BulletItem(stringResource(R.string.cloud_apis_openai_anthropic_groq_etc_select_c))
                BulletItem(stringResource(R.string.tap_test_connection_to_verify_a_green_connect))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.step_3_start_chatting))
                BulletItem(stringResource(R.string.go_to_characters_and_tap_a_character_to_open))
                BulletItem(stringResource(R.string.type_a_message_and_tap_send_the_ai_response_s))
                BulletItem(stringResource(R.string.your_chat_is_saved_automatically_find_it_agai))
            }

            // ── Home Screen ─────────────────────────────────────────
            HelpDropdown(title = "Home Screen") {
                HelpText(stringResource(R.string.the_home_screen_is_your_main_hub_with_five_na))
                BulletItem(stringResource(R.string.characters_browse_import_and_manage_your_char))
                BulletItem(stringResource(R.string.recent_chats_shows_your_latest_conversations))
                BulletItem(stringResource(R.string.create_character_jumps_directly_to_the_charac))
                BulletItem(stringResource(R.string.charavault_browse_and_import_community_charac))
                BulletItem(stringResource(R.string.settings_access_all_app_settings_and_configur))
                VerticalSpacer()
                HelpText(stringResource(R.string.the_bottom_of_the_screen_shows_your_current_c))
                HelpText(stringResource(R.string.if_a_new_app_version_is_available_an_update_d))
            }

            // ── Chat ────────────────────────────────────────────────
            HelpDropdown(title = "Chat") {
                SectionHeading(stringResource(R.string.sending_messages))
                BulletItem(stringResource(R.string.type_in_the_message_box_at_the_bottom_and_tap))
                BulletItem(stringResource(R.string.you_can_also_use_the_keyboard_s_send_done_act))
                BulletItem(stringResource(R.string.the_sys_prefix_sends_a_narrator_system_messag))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.controls_below_messages))
                BulletItem(stringResource(R.string.stop_while_the_ai_is_generating_a_red_stop_bu))
                BulletItem(stringResource(R.string.regenerate_after_an_ai_response_tap_to_delete))
                BulletItem(stringResource(R.string.continue_tap_to_append_more_text_to_the_last))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.alternative_responses_swipes))
                BulletItem(stringResource(R.string.swipe_left_or_right_on_any_ai_message_to_cycl))
                BulletItem(stringResource(R.string.a_counter_e_g_1_3_appears_below_ai_messages_w))
                BulletItem(stringResource(R.string.tap_the_left_right_arrows_to_navigate_the_ref))
                BulletItem(stringResource(R.string.all_alternatives_are_saved_you_can_flip_back))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.long_press_actions))
                HelpText(stringResource(R.string.long_press_any_message_bubble_to_open_the_act))
                BulletItem(stringResource(R.string.edit_message_opens_an_editor_to_modify_the_me))
                BulletItem(stringResource(R.string.delete_message_removes_just_that_message))
                BulletItem(stringResource(R.string.delete_from_here_removes_this_message_and_eve))
                BulletItem(stringResource(R.string.regenerate_last_ai_message_only_regenerates_t))
                BulletItem(stringResource(R.string.generate_image_opens_the_image_generation_dia))
                BulletItem(stringResource(R.string.play_tts_stop_tts_when_tts_is_enabled_speak_o))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.top_bar_menu))
                HelpText(stringResource(R.string.tap_the_three_dot_menu_top_right_to_access))
                BulletItem(stringResource(R.string.chat_history_view_and_switch_between_saved_ch))
                BulletItem(stringResource(R.string.new_chat_start_a_fresh_conversation_if_the_ch))
                BulletItem(stringResource(R.string.character_settings_open_per_character_setting))
                BulletItem(stringResource(R.string.edit_character_open_the_character_editor))
                BulletItem(stringResource(R.string.upload_background_pick_an_image_from_your_dev))
                BulletItem(stringResource(R.string.clear_background_remove_the_current_chat_back))
                BulletItem(stringResource(R.string.debug_log_view_the_app_s_debug_log_for_troubl))
                BulletItem(stringResource(R.string.delete_chat_delete_character_destructive_acti))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.status_bar))
                HelpText(stringResource(R.string.above_the_input_bar_a_small_status_line_shows))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.quick_reply_buttons))
                HelpText(stringResource(R.string.when_the_quick_reply_extension_is_enabled_pre))
            }

            // ── Characters ──────────────────────────────────────────
            HelpDropdown(title = "Characters & Cards") {
                SectionHeading(stringResource(R.string.character_list))
                BulletItem(stringResource(R.string.tap_a_character_to_start_or_resume_a_chat))
                BulletItem(stringResource(R.string.long_press_a_character_to_open_the_action_men))
                BulletItem(stringResource(R.string.tap_the_button_top_right_to_create_a_new_char))
                BulletItem(stringResource(R.string.tap_the_import_button_to_import_a_png_charact))
                BulletItem(stringResource(R.string.switch_between_characters_and_groups_using_th))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.creating_editing_a_character))
                HelpText(stringResource(R.string.the_character_editor_has_five_tabs))
                BulletItem(stringResource(R.string.basic_name_required_and_avatar_tap_the_avatar))
                BulletItem(stringResource(R.string.personality_description_personality_and_scena))
                BulletItem(stringResource(R.string.messages_first_message_alternate_greetings_ad))
                BulletItem(stringResource(R.string.advanced_character_specific_system_prompt_use))
                BulletItem(stringResource(R.string.meta_creator_name_tags_type_and_tap_add_and_c))
                VerticalSpacer()
                HelpText(stringResource(R.string.tap_save_create_top_right_when_done))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.character_settings_per_character))
                HelpText(stringResource(R.string.open_from_the_character_s_long_press_menu_or))
                BulletItem(stringResource(R.string.favorite_toggle_heart_icon_in_the_top_bar_fav))
                BulletItem(stringResource(R.string.attached_lorebook_select_a_lorebook_to_attach))
                BulletItem(stringResource(R.string.system_prompt_override_the_global_system_prom))
                BulletItem(stringResource(R.string.author_s_note_per_character_author_s_note_wit))
                BulletItem(stringResource(R.string.talkativeness_slider_controlling_how_often_th))
                BulletItem(stringResource(R.string.tts_voice_select_a_voice_override_for_this_ch))
            }

            // ── Group Chat ──────────────────────────────────────────
            HelpDropdown(title = "Group Chats") {
                HelpText(stringResource(R.string.chat_with_multiple_ai_characters_simultaneous))
                VerticalSpacer()
                SectionHeading(stringResource(R.string.creating_a_group))
                BulletItem(stringResource(R.string.go_to_characters_switch_to_the_groups_tab_tit))
                BulletItem(stringResource(R.string.enter_a_group_name_and_select_at_least_2_char))
                BulletItem(stringResource(R.string.tap_create))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.group_chat_controls))
                BulletItem(stringResource(R.string.the_top_bar_shows_the_group_name_and_member_c))
                BulletItem(stringResource(R.string.tap_the_three_dot_menu_to_change_the_activati))
                BulletItem(stringResource(R.string.natural_characters_respond_in_a_natural_conve))
                BulletItem(stringResource(R.string.list_all_respond_all_characters_respond_in_li))
                BulletItem(stringResource(R.string.pooled_characters_are_drawn_from_a_pool))
                BulletItem(stringResource(R.string.manual_you_choose_which_character_responds_ea))
                VerticalSpacer()
                BulletItem(stringResource(R.string.long_press_a_group_in_the_list_to_delete_it))
            }

            // ── LLM Backends ────────────────────────────────────────
            HelpDropdown(title = "LLM Backends") {
                SectionHeading(stringResource(R.string.api_configuration))
                HelpText(stringResource(R.string.go_to_settings_api_configuration_to_connect_t))
                BulletItem(stringResource(R.string.select_the_api_type_from_the_dropdown_text_co))
                BulletItem(stringResource(R.string.enter_the_server_url_and_api_key_if_required))
                BulletItem(stringResource(R.string.tap_the_refresh_icon_to_fetch_available_model))
                BulletItem(stringResource(R.string.select_your_model_from_the_dropdown_or_type_i))
                BulletItem(stringResource(R.string.tap_test_connection_to_verify))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.text_completion_backends))
                HelpText(stringResource(R.string.for_local_inference_servers_that_use_raw_text))
                BulletItem(stringResource(R.string.koboldcpp_http_your_ip_5001_start_with_host_0))
                BulletItem(stringResource(R.string.llama_cpp_http_your_ip_8080))
                BulletItem(stringResource(R.string.ollama_http_your_ip_11434_set_ollama_host_0_0))
                BulletItem(stringResource(R.string.text_gen_webui_ooba_vllm_aphrodite_tabbyapi))
                BulletItem(stringResource(R.string.cloud_together_ai_openrouter_infermatic_ai_fe))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.chat_completion_backends))
                HelpText(stringResource(R.string.for_apis_that_use_the_chat_message_format))
                BulletItem(stringResource(R.string.openai_anthropic_claude_google_ai_studio_gemi))
                BulletItem(stringResource(R.string.deepseek_mistral_ai_groq_cohere_perplexity))
                BulletItem(stringResource(R.string.openrouter_xai_grok_fireworks_ai21_vertex_ai))
                BulletItem(stringResource(R.string.pollinations_and_many_more))
                BulletItem(stringResource(R.string.custom_any_openai_compatible_endpoint_via_cus))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.connection_profiles))
                HelpText(stringResource(R.string.go_to_settings_connection_profiles_to_save_mu))
            }

            // ── Image Generation ────────────────────────────────────
            HelpDropdown(title = "Image Generation") {
                SectionHeading(stringResource(R.string.supported_backends))
                BulletItem(stringResource(R.string.sd_webui_forge_local_server_requires_api_flag))
                BulletItem(stringResource(R.string.comfyui_local_node_graph_server_enter_url_in))
                BulletItem(stringResource(R.string.dall_e_openai_cloud_api_enter_api_key_support))
                BulletItem(stringResource(R.string.stability_ai_cloud_api_enter_api_key))
                BulletItem(stringResource(R.string.pollinations_pay_as_you_go_pollen_credits_ent))
                BulletItem(stringResource(R.string.huggingface_cloud_api_enter_api_key_and_model))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.generating_from_chat))
                BulletItem(stringResource(R.string.long_press_any_message_generate_image))
                BulletItem(stringResource(R.string.choose_a_mode_background_landscape_scene_or_c))
                BulletItem(stringResource(R.string.your_llm_automatically_generates_an_image_pro))
                BulletItem(stringResource(R.string.edit_the_prompt_if_desired_then_tap_generate))
                BulletItem(stringResource(R.string.when_complete_save_to_gallery_or_set_as_chat))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.per_character_avatar_toggle))
                HelpText(stringResource(R.string.in_character_mode_a_use_avatar_as_reference_s))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.avatar_generation))
                HelpText(stringResource(R.string.when_creating_or_editing_a_character_tap_the))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.settings))
                HelpText(stringResource(R.string.configure_under_settings_image_generation))
                BulletItem(stringResource(R.string.backend_selector_switch_between_all_six_backe))
                BulletItem(stringResource(R.string.url_api_key_shown_only_when_the_active_backen))
                BulletItem(stringResource(R.string.sampler_and_model_fetched_dynamically_from_sd))
                BulletItem(stringResource(R.string.steps_1_150_cfg_scale_1_30_seed_1_random))
                BulletItem(stringResource(R.string.resolution_presets_portrait_landscape_square))
                BulletItem(stringResource(R.string.negative_prompt_text_describing_what_to_avoid))
                BulletItem(stringResource(R.string.clip_skip_sd_webui_forge_only))
                BulletItem(stringResource(R.string.test_connection_verify_the_backend_is_reachab))
            }

            // ── Text-to-Speech ──────────────────────────────────────
            HelpDropdown(title = "Text-to-Speech (TTS)") {
                SectionHeading(stringResource(R.string.providers))
                BulletItem(stringResource(R.string.system_tts_uses_android_s_built_in_speech_eng))
                BulletItem(stringResource(R.string.openai_compatible_sends_text_to_any_server_im))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.configuration))
                HelpText(stringResource(R.string.go_to_settings_text_to_speech))
                BulletItem(stringResource(R.string.provider_choose_system_or_openai_compatible))
                BulletItem(stringResource(R.string.auto_play_automatically_speak_new_ai_messages))
                BulletItem(stringResource(R.string.speed_playback_rate_from_0_5x_to_2_0x))
                BulletItem(stringResource(R.string.voice_select_from_available_voices_fetched_fr))
                BulletItem(stringResource(R.string.filter_mode_control_what_gets_spoken_all_text))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.per_character_voices))
                HelpText(stringResource(R.string.each_character_can_have_its_own_voice_overrid))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.manual_playback))
                HelpText(stringResource(R.string.long_press_any_message_in_chat_to_access_play))
            }

            // ── World Info ──────────────────────────────────────────
            HelpDropdown(title = "World Info & Lorebooks") {
                HelpText(stringResource(R.string.lorebooks_inject_relevant_lore_into_the_ai_s))
                VerticalSpacer()
                SectionHeading(stringResource(R.string.how_it_works))
                BulletItem(stringResource(R.string.each_lorebook_entry_has_keywords_when_those_k))
                BulletItem(stringResource(R.string.secondary_keys_provide_an_and_filter_both_pri))
                BulletItem(stringResource(R.string.constant_entries_are_always_active_and_bypass))
                BulletItem(stringResource(R.string.recursive_scanning_checks_activated_entries_f))
                BulletItem(stringResource(R.string.regex_keys_pattern_flags_enable_advanced_patt))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.entry_settings))
                BulletItem(stringResource(R.string.insertion_position_before_char_after_char_top))
                BulletItem(stringResource(R.string.probability_activation_chance_0_100))
                BulletItem(stringResource(R.string.token_budget_stops_injecting_once_the_budget))
                BulletItem(stringResource(R.string.entry_groups_organize_entries_into_named_grou))
                BulletItem(stringResource(R.string.selective_flag_enables_secondary_keys_for_fin))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.attaching_lorebooks))
                BulletItem(stringResource(R.string.globally_go_to_settings_world_info_lorebooks))
                BulletItem(stringResource(R.string.per_character_go_to_character_settings_attach))
                BulletItem(stringResource(R.string.per_persona_edit_a_persona_and_attach_a_loreb))
                BulletItem(stringResource(R.string.embedded_character_cards_with_embedded_charac))
                BulletItem(stringResource(R.string.from_charavault_browse_and_import_community_l))
            }

            // ── Personas ────────────────────────────────────────────
            HelpDropdown(title = "User Personas") {
                HelpText(stringResource(R.string.personas_tell_the_ai_who_it_s_talking_to_you))
                VerticalSpacer()
                SectionHeading(stringResource(R.string.creating_a_persona))
                BulletItem(stringResource(R.string.go_to_settings_personas_and_tap_the_button))
                BulletItem(stringResource(R.string.choose_an_avatar_pick_from_gallery_or_generat))
                BulletItem(stringResource(R.string.enter_a_display_name_and_optional_description))
                BulletItem(stringResource(R.string.tap_create))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.editing_a_persona))
                HelpText(stringResource(R.string.tap_the_edit_icon_on_any_persona_to_configure))
                BulletItem(stringResource(R.string.description_injected_into_the_prompt_so_chara))
                BulletItem(stringResource(R.string.position_where_the_description_appears_in_sys))
                BulletItem(stringResource(R.string.role_system_user_or_assistant))
                BulletItem(stringResource(R.string.depth_when_position_is_in_chat_at_depth_how_m))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.switching_personas))
                HelpText(stringResource(R.string.tap_any_persona_in_the_list_to_make_it_active))
            }

            // ── Prompt Building ──────────────────────────────────────
            HelpDropdown(title = "Prompt Building & Templates") {
                HelpText(stringResource(R.string.pockettavern_ships_with_96_bundled_templates))
                VerticalSpacer()
                SectionHeading(stringResource(R.string.instruct_templates_42_bundled))
                HelpText(stringResource(R.string.wraps_each_message_in_the_correct_tokens_for))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.context_templates_34_bundled))
                HelpText(stringResource(R.string.controls_how_character_description_persona_sc))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.textgen_presets_6_bundled))
                HelpText(stringResource(R.string.sampler_parameter_sets_for_text_completion_ba))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.system_prompt_presets_14_bundled))
                HelpText(stringResource(R.string.ready_to_use_system_prompts_roleplay_immersiv))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.chat_completion_presets))
                HelpText(stringResource(R.string.for_chat_completion_apis_openai_claude_etc_go))
            }

            // ── Generation Settings ─────────────────────────────────
            HelpDropdown(title = "Text Generation Settings") {
                SectionHeading(stringResource(R.string.text_completion_local_backends))
                HelpText(stringResource(R.string.settings_text_generation_key_parameters))
                BulletItem(stringResource(R.string.temperature_randomness_of_output_higher_more))
                BulletItem(stringResource(R.string.top_p_top_k_min_p_top_a_sampling_filters_that))
                BulletItem(stringResource(R.string.repetition_penalty_discourages_the_model_from))
                BulletItem(stringResource(R.string.max_new_tokens_maximum_response_length))
                BulletItem(stringResource(R.string.context_size_how_much_chat_history_fits_in_th))
                BulletItem(stringResource(R.string.dry_sampler_advanced_repetition_suppression))
                BulletItem(stringResource(R.string.mirostat_alternative_sampling_mode_with_targe))
                BulletItem(stringResource(R.string.load_and_save_named_presets_e_g_universal_cre))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.chat_completion_cloud_apis))
                HelpText(stringResource(R.string.settings_chat_completion_presets_key_paramete))
                BulletItem(stringResource(R.string.temperature_top_p_top_k_same_as_above_but_for))
                BulletItem(stringResource(R.string.max_tokens_maximum_response_length))
                BulletItem(stringResource(R.string.frequency_penalty_presence_penalty_repetition))
                BulletItem(stringResource(R.string.context_size_seed_additional_controls))
                BulletItem(stringResource(R.string.prompt_order_editor_drag_and_reorder_prompt_b))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.author_s_note_3))
                HelpText(stringResource(R.string.settings_context_settings_inject_custom_text))
                BulletItem(stringResource(R.string.content_the_text_to_inject))
                BulletItem(stringResource(R.string.depth_how_many_messages_from_the_end_to_place))
                BulletItem(stringResource(R.string.interval_how_often_to_inject_every_n_messages))
                BulletItem(stringResource(R.string.position_before_char_after_char_top_bottom_of))
                BulletItem(stringResource(R.string.role_system_user_or_assistant))
            }

            // ── CharaVault ──────────────────────────────────────────
            HelpDropdown(title = "CharaVault") {
                HelpText(stringResource(R.string.browse_and_import_community_characters_and_lo))
                VerticalSpacer()
                SectionHeading(stringResource(R.string.modes))
                BulletItem(stringResource(R.string.charavault_net_browse_the_public_catalog_gues))
                BulletItem(stringResource(R.string.self_hosted_connect_to_your_own_charavault_se))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.browsing))
                BulletItem(stringResource(R.string.search_by_name_or_description_using_the_searc))
                BulletItem(stringResource(R.string.filter_by_tags_tap_the_tag_icon_to_open_the_t))
                BulletItem(stringResource(R.string.content_filter_tap_the_filter_icon_to_toggle))
                BulletItem(stringResource(R.string.switch_between_characters_and_lorebooks_using))
                BulletItem(stringResource(R.string.navigate_pages_with_the_previous_next_buttons))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.importing_2))
                BulletItem(stringResource(R.string.tap_any_card_to_open_a_preview_with_full_deta))
                BulletItem(stringResource(R.string.tap_import_to_pockettavern_to_download_and_sa))
                BulletItem(stringResource(R.string.tap_tags_in_the_preview_to_add_them_to_your_s))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.uploading))
                HelpText(stringResource(R.string.long_press_a_character_in_the_characters_list))
            }

            // ── Extensions ──────────────────────────────────────────
            HelpDropdown(title = "Extensions") {
                SectionHeading(stringResource(R.string.built_in_extensions))
                HelpText(stringResource(R.string.go_to_settings_extensions_to_enable_disable))
                BulletItem(stringResource(R.string.quick_reply_preset_buttons_above_the_chat_inp))
                BulletItem(stringResource(R.string.regex_find_and_replace_rules_applied_to_ai_ou))
                BulletItem(stringResource(R.string.token_counter_shows_a_live_estimated_token_co))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.javascript_extensions))
                HelpText(stringResource(R.string.install_third_party_extensions_that_run_in_a))
                BulletItem(stringResource(R.string.tap_the_button_in_the_javascript_extensions_s))
                BulletItem(stringResource(R.string.install_from_url_enter_the_url_of_the_extensi))
                BulletItem(stringResource(R.string.install_from_device_browse_for_a_js_file_or_z))
                BulletItem(stringResource(R.string.toggle_extensions_on_off_with_the_switch_on_e))
                BulletItem(stringResource(R.string.some_extensions_have_configurable_settings_ta))
                BulletItem(stringResource(R.string.tap_uninstall_with_confirmation_to_remove_an))
            }

            // ── Themes ──────────────────────────────────────────────
            HelpDropdown(title = "Themes & Appearance") {
                SectionHeading(stringResource(R.string.applying_a_theme))
                HelpText(stringResource(R.string.go_to_settings_appearance_tap_apply_on_any_th))
                VerticalSpacer()
                SectionHeading(stringResource(R.string.built_in_themes))
                BulletItem(stringResource(R.string.pockettavern_default_fire_ice_with_animated_p))
                BulletItem(stringResource(R.string.fire_ice_the_default_theme_exported_as_editab))
                BulletItem(stringResource(R.string.midnight_plum_purple_stars_rising_with_slow_f))
                BulletItem(stringResource(R.string.ember_warm_embers_with_bright_spark_accents))
                BulletItem(stringResource(R.string.sand_and_sea_warm_sandy_tones_with_ocean_blue))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.importing_themes))
                BulletItem(stringResource(R.string.tap_import_theme_json_or_zip_at_the_bottom_of))
                BulletItem(stringResource(R.string.json_files_sillytavern_theme_exports_or_pocke))
                BulletItem(stringResource(R.string.zip_bundles_include_theme_json_plus_optional))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.theme_features))
                BulletItem(stringResource(R.string.animated_backgrounds_gif_or_animated_webp_fil))
                BulletItem(stringResource(R.string.theme_logos_replace_the_pockettavern_logo_on))
                BulletItem(stringResource(R.string.background_music_mp3_ogg_or_wav_files_play_wh))
                BulletItem(stringResource(R.string.particle_effects_animated_background_particle))
                BulletItem(stringResource(R.string.delete_imported_themes_with_the_trash_icon_bu))
            }

            // ── SillyTavern Import ──────────────────────────────────
            HelpDropdown(title = "SillyTavern Import") {
                HelpText(stringResource(R.string.migrate_your_characters_chats_and_lorebooks_f))
                VerticalSpacer()
                SectionHeading(stringResource(R.string.from_server))
                BulletItem(stringResource(R.string.go_to_settings_import_from_sillytavern))
                BulletItem(stringResource(R.string.enter_your_sillytavern_server_url_and_credent))
                BulletItem(stringResource(R.string.select_what_to_import_characters_chats_lorebo))
                BulletItem(stringResource(R.string.tap_import_everything_is_pulled_down_and_save))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.from_folder))
                BulletItem(stringResource(R.string.copy_your_sillytavern_data_directory_to_your))
                BulletItem(stringResource(R.string.choose_import_from_folder_and_use_the_folder))
                BulletItem(stringResource(R.string.characters_chats_and_lorebooks_are_scanned_an))

                VerticalSpacer()
                HelpText(stringResource(R.string.after_import_pockettavern_works_completely_in))
            }

            // ── Settings Overview ───────────────────────────────────
            HelpDropdown(title = "Settings Overview") {
                HelpText(stringResource(R.string.settings_are_organized_into_five_groups))
                VerticalSpacer()
                SectionHeading(stringResource(R.string.connection))
                BulletItem(stringResource(R.string.api_configuration_select_backend_type_url_api))
                BulletItem(stringResource(R.string.connection_profiles_save_and_switch_between_m))
                BulletItem(stringResource(R.string.image_generation_configure_image_generation_b))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.generation))
                BulletItem(stringResource(R.string.text_generation_sampler_settings_and_presets))
                BulletItem(stringResource(R.string.chat_completion_presets_sampling_presets_and))
                BulletItem(stringResource(R.string.formatting_instruct_templates_context_templat))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.world_characters))
                BulletItem(stringResource(R.string.world_info_lorebooks_view_and_manage_lorebook))
                BulletItem(stringResource(R.string.context_settings_global_author_s_note_configu))
                BulletItem(stringResource(R.string.personas_create_and_manage_user_personas_show))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.appearance_audio))
                BulletItem(stringResource(R.string.appearance_import_and_apply_themes_json_or_zi))
                BulletItem(stringResource(R.string.text_to_speech_voice_synthesis_settings_syste))

                VerticalSpacer()
                SectionHeading(stringResource(R.string.utilities))
                BulletItem(stringResource(R.string.extensions_quick_reply_regex_token_counter_an))
                BulletItem(stringResource(R.string.import_from_sillytavern_migrate_characters_ch))
                BulletItem(stringResource(R.string.help_this_screen))
                VerticalSpacer()
                HelpText(stringResource(R.string.a_connection_status_indicator_at_the_bottom_s))
            }

            // ── FAQ ─────────────────────────────────────────────────
            HelpDropdown(title = "Frequently Asked Questions") {
                FaqItem(
                    question = "Do I need a SillyTavern server to use PocketTavern?",
                    answer = "No. PocketTavern is fully standalone. It connects directly to LLM backends (KoboldCpp, Ollama, OpenAI, etc.) with no middleman. SillyTavern is not required."
                )
                FaqItem(
                    question = "What character card formats are supported?",
                    answer = "PocketTavern uses PNG character cards with embedded metadata (V2 spec) — the same format SillyTavern uses. Any .png card exported from SillyTavern, CharaVault, Chub.ai, or similar tools will work."
                )
                FaqItem(
                    question = "Can I use a local AI model on my phone?",
                    answer = "Yes — PocketTavern now runs models on-device: pick an on-device model in the app and it downloads and runs locally, with hardware (NPU/GPU) acceleration on supported phones. You can also still connect to an LLM server on your PC (KoboldCpp, Ollama, LM Studio) over Wi-Fi, or use a cloud API."
                )
                FaqItem(
                    question = "How do I connect to a local backend like KoboldCpp?",
                    answer = "1) Start KoboldCpp on your PC with --host 0.0.0.0 so it listens on the network.\n2) Find your PC's local IP (e.g. 192.168.1.100).\n3) In PocketTavern: Settings → API Configuration → enter http://192.168.1.100:5001.\n4) Your phone must be on the same Wi-Fi network."
                )
                FaqItem(
                    question = "Why can't I connect to my local backend?",
                    answer = "Common causes:\n• The backend isn't listening on the network (use --host 0.0.0.0 for KoboldCpp, OLLAMA_HOST=0.0.0.0 for Ollama).\n• Using \"localhost\" or \"127.0.0.1\" instead of your PC's actual IP address.\n• Phone and PC are on different Wi-Fi networks.\n• Firewall is blocking the port.\n• The URL has a trailing slash."
                )
                FaqItem(
                    question = "How do I use OpenAI / Claude / Groq?",
                    answer = "Settings → API Configuration → select Chat Completion as the API type. Choose your provider, enter the API URL and your API key, then tap the refresh button to load models. Select a model and tap Test Connection."
                )
                FaqItem(
                    question = "What's the difference between Text Completion and Chat Completion?",
                    answer = "Text Completion backends (KoboldCpp, llama.cpp, etc.) take a single text prompt. They need instruct templates and context templates to format the conversation correctly.\n\nChat Completion backends (OpenAI, Claude, etc.) take a list of messages with roles. They use the Chat Completion Presets and prompt order editor instead."
                )
                FaqItem(
                    question = "How do I get the AI to follow my instructions better?",
                    answer = "• Put key instructions in the character's System Prompt (Character Settings).\n• Use Author's Note (Settings → Context Settings) at a low depth for persistent steering.\n• For text completion: make sure you're using the correct instruct template for your model.\n• For chat completion: edit the Main Prompt in Chat Completion Presets."
                )
                FaqItem(
                    question = "Can I use multiple AI models?",
                    answer = "Yes. Use Connection Profiles (Settings → Connection Profiles) to save different API configurations and switch between them instantly."
                )
                FaqItem(
                    question = "How does image generation work?",
                    answer = "Long-press a message → Generate Image. Choose Background or Character mode. The LLM generates an image prompt from the message context, which is sent to your configured image backend. You can edit the prompt before generating. Configure your backend in Settings → Image Generation."
                )
                FaqItem(
                    question = "Do I need to pay for image generation?",
                    answer = "SD WebUI / Forge and ComfyUI are free but require running a local server. Pollinations, DALL-E, Stability AI, and HuggingFace require paid API keys. Pollinations uses a pay-as-you-go \"Pollen credits\" system — get a key at pollinations.ai."
                )
                FaqItem(
                    question = "What are swipes?",
                    answer = "Swipes are alternative AI responses. Swipe left/right on any AI message (or use the arrow buttons below it) to cycle through alternatives. The refresh button generates a new alternative. All alternatives are saved."
                )
                FaqItem(
                    question = "How do I import my SillyTavern data?",
                    answer = "Settings → Import from SillyTavern. You can import from a running SillyTavern server (enter URL and credentials) or from a folder on your device containing SillyTavern data files."
                )
                FaqItem(
                    question = "Where are my files stored?",
                    answer = "Characters, chats, lorebooks, themes, and settings are stored in PocketTavern's private app storage on your device. Chats use the SillyTavern-compatible .jsonl format and characters use standard PNG cards, so nothing is locked in."
                )
                FaqItem(
                    question = "How do I back up my data?",
                    answer = "Chat files (.jsonl) and character cards (.png) are stored in the app's internal storage. You can export individual character cards. For a full backup, use Android's file manager or ADB to copy the app's data directory."
                )
                FaqItem(
                    question = "What are extensions?",
                    answer = "Extensions add features to PocketTavern. Three are built-in (Quick Reply, Regex, Token Counter). You can also install JavaScript extensions from URLs that react to chat events, inject prompts, show custom UI, and more. Configure them in Settings → Extensions."
                )
                FaqItem(
                    question = "Can I use SillyTavern themes?",
                    answer = "Yes. Export a theme from SillyTavern as a .json file, transfer it to your device, and import it via Settings → Appearance → Import Theme. PocketTavern reads the color fields and applies them. Web-specific CSS fields are ignored."
                )
            }

            // ── Troubleshooting ─────────────────────────────────────
            HelpDropdown(title = "Troubleshooting") {
                TroubleshootBlock(
                    problem = "Can't connect to local backend",
                    solutions = listOf(
                        "Make sure the backend is running and listening on the network",
                        "Use your PC's local IP address (e.g. 192.168.1.x), not localhost or 127.0.0.1",
                        "Phone and PC must be on the same Wi-Fi network",
                        "Check that your firewall allows the port (5001, 11434, etc.)",
                        "For KoboldCpp: start with --host 0.0.0.0 flag",
                        "For Ollama: set OLLAMA_HOST=0.0.0.0 before starting",
                        "Make sure the URL does not have a trailing slash"
                    )
                )
                TroubleshootBlock(
                    problem = "Cloud API not working",
                    solutions = listOf(
                        "Double-check your API key is correct and has credits/quota remaining",
                        "Make sure the API URL does not have a trailing slash",
                        "Tap the model refresh button after entering the URL and key",
                        "Check the Debug Log (chat menu → Debug Log) for error details"
                    )
                )
                TroubleshootBlock(
                    problem = "AI gives short or empty responses",
                    solutions = listOf(
                        "Increase Max Tokens / Max New Tokens in generation settings",
                        "Check that your context size isn't too small for the conversation history",
                        "Some models need a specific instruct template — make sure you've selected the right one in Formatting"
                    )
                )
                TroubleshootBlock(
                    problem = "AI ignores instructions / Author's Note",
                    solutions = listOf(
                        "Try putting key instructions in the character's System Prompt instead",
                        "Reduce Author's Note depth (lower = closer to the end = more influential)",
                        "For chat completion: edit the Main Prompt in Chat Completion Presets",
                        "Some models (especially Claude) anchor heavily to their initial system prompt"
                    )
                )
                TroubleshootBlock(
                    problem = "Characters not loading or showing errors",
                    solutions = listOf(
                        "Check that the PNG file is a valid character card (exported from SillyTavern or similar)",
                        "Try re-importing the PNG file",
                        "Check the Debug Log for parse errors",
                        "Some very old card formats (V1) may not include all fields — try a V2 card"
                    )
                )
                TroubleshootBlock(
                    problem = "Image generation fails",
                    solutions = listOf(
                        "Verify the backend is running and reachable (use Test Connection in Image Generation settings)",
                        "For SD WebUI / Forge: make sure it was started with the --api flag",
                        "For cloud backends: check that your API key is valid and has credits",
                        "Check the Debug Log for detailed error messages"
                    )
                )
                TroubleshootBlock(
                    problem = "TTS not working",
                    solutions = listOf(
                        "For System TTS: make sure your device has a TTS engine installed (most Android devices do by default)",
                        "For OpenAI-compatible: verify the server URL and that the server is running",
                        "Try the Test Voice button in TTS settings",
                        "Check that TTS is enabled in Settings → Text-to-Speech"
                    )
                )
                TroubleshootBlock(
                    problem = "Theme not applying correctly",
                    solutions = listOf(
                        "Make sure the theme file is valid JSON",
                        "For ZIP bundles: the ZIP must contain a theme.json at the root level",
                        "ZIP bundles are limited to 50 MB",
                        "Some SillyTavern theme fields (CSS, font scale) are not applicable to Android and are ignored"
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Reusable Components ─────────────────────────────────────────────

@Composable
private fun HelpDropdown(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = if (expanded) MaterialTheme.shapes.medium else MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun HelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BulletItem(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "\u2022 ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VerticalSpacer() {
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }
        if (!expanded) {
            Spacer(modifier = Modifier.height(4.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun TroubleshootBlock(problem: String, solutions: List<String>) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = problem,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        solutions.forEach { solution ->
            BulletItem(solution)
        }
    }
}

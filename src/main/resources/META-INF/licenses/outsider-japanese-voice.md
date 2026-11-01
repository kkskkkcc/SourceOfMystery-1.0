# 域外魔导师：正式角色语音 V3

用户于2026-10-19明确通过陨灭杀试听，并授权其他日常语言和招式统一采用该声线。
批准母带：v2/faster-higher-tail/ultimate-faster-higher-tail2s.wav
SHA256：b304f65262a3cee6f2d46f410239ab624bfd7ef38bcbc85249a40e0b41045099

模型：Qwen3-TTS-12Hz-1.7B-Base。使用已通过母带前3.1秒、本项目自行合成的角色声音作为统一参考，没有真实个人录音。
初始合成来源为Qwen3-TTS CustomVoice预置日语声线Ono_Anna；正式全套由Base的参考音频/文本流程生成，保持人物声线。
官方项目：https://github.com/QwenLM/Qwen3-TTS
官方Base模型：https://huggingface.co/Qwen/Qwen3-TTS-12Hz-1.7B-Base
官方镜像：https://modelscope.cn/models/Qwen/Qwen3-TTS-12Hz-1.7B-Base
模型及工具包许可Apache-2.0。下载文件SHA256清单位于build/archmage-tts/model-base/source-files.json。

全套17个语音事件、16个独立录音：交战、普通两招、大招宣言、吟唱、正式喊招、击杀、死亡及地面/巡游/起飞/落地日常。
陨灭杀两个备用事件都使用用户批准的同一母带，避免随机播出已否决版本；WAV逐字节保持母带不变。
普通与日常录音仅做带限、去静音和音量处理，未统一拉高音调；对应情绪由语句及参考声音自然生成。
陨灭杀维持已批准的后半段1.15倍速、+1.5半音，共振峰保留，末字2秒渐弱扩散回响。
该历史后处理使用Rubber Band4.0 R3，官方：https://www.breakfastquay.com/rubberband/ 。GPL工具、许可及来源在build/archmage-tts/rubberband；工具不打包进模组。

每句对应眉眼/嘴角表演，录音20Hz包络驱动嘴型并保留自然眨眼；陨灭杀使用干声包络，回响期间合口。
日常台词仅在无战斗目标时低频播放；战斗台词可立即打断日常，大招喊招/死亡有最高优先级，只打断同一BOSS。
狂风、雷声和施法声音会避让人声。狂风由本项目自行合成，generate_ritual_wind.py可重建。

生成入口：tools/outsider/generate_archmage_voice_v3.py；--package-only仅重新打包已生成录音。
运行环境：build/archmage-tts/venv，PyTorch2.8+cu128、qwen-tts0.1.1；模型和GPU依赖只用于构建，不进入游戏JAR。
运行时仅加载17条单声道24kHz OGG、小型口型数据和生成的时长表。旧候选与已否决录音保留在voice/v1、voice/v2，不能用于发布。
台词、时长和声线参照哈希：v3/lines.json；批准记录：v3/approval.json；完整试听：v3/voice-v3-all-lines.wav。

"""Original skill effects, deterministic PCM synthesis at 44.1 kHz."""
from pathlib import Path
import hashlib
import json
import math
import random
import struct
import wave

folder = Path(__file__).resolve().parents[1] / 'src/main/resources/sounds'
manifest = json.loads((folder / 'manifest.json').read_text(encoding='utf-8'))
for name, duration in [('skill_sonar', 1.2), ('skill_mine', 1.7), ('skill_shield', 1.1),
                       ('skill_shield_break', .65), ('skill_airstrike', 2.8)]:
    randomizer = random.Random(name)
    samples = bytearray()
    filtered = 0
    for index in range(round(duration * 44100)):
        time = index / 44100
        noise = randomizer.uniform(-1, 1)
        filtered = .96 * filtered + .04 * noise
        envelope = min(1, time * 50) * min(1, (duration - time) * 12)
        if name == 'skill_sonar':
            pulse = time % .4
            sample = math.sin(2 * math.pi * (1200 * time - 95 * time * time)) * math.exp(-pulse * 17) * .65
        elif name == 'skill_mine':
            sample = (math.sin(2 * math.pi * (68 * time - 12 * time * time)) * .55 + filtered * 3) * math.exp(-time * 3)
        elif name == 'skill_shield':
            sample = (math.sin(2 * math.pi * (180 * time + 150 * time * time)) + .3 * math.sin(2 * math.pi * 720 * time)) * .3
        elif name == 'skill_shield_break':
            sample = (noise * .45 + math.sin(2 * math.pi * 2150 * time) * .3) * math.exp(-time * 7)
        else:
            sample = (filtered * 3 + math.sin(2 * math.pi * (80 * time + 35 * time * time)) * .15) * math.sin(math.pi * time / duration)
        value = max(-32767, min(32767, round(sample * envelope * 27000)))
        samples.extend(struct.pack('<h', value))
    target = folder / (name + '.wav')
    with wave.open(str(target), 'wb') as output:
        output.setnchannels(1); output.setsampwidth(2); output.setframerate(44100); output.writeframes(samples)
    manifest[target.name] = dict(source_file='frontend/scripts/generate-skill-audio.py', source_page='Original project synthesis',
                                 author='Battleship project', license='CC0-1.0', loop=False, seconds=duration,
                                 sha256=hashlib.sha256(target.read_bytes()).hexdigest(), edits='Original PCM16 mono synthesis')
(folder / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

"""Download licensed source audio and prepare the game's PCM WAV assets.

Run with Python plus requests, numpy and scipy (or set FFMPEG for your converter).
Downloads stay in ignored build/audio-sources; provenance is saved beside WAVs.
"""
from pathlib import Path
from html.parser import HTMLParser
import hashlib
import json
import os
import re
import subprocess
import zipfile
import requests

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / 'build' / 'audio-sources'
OUTPUT = ROOT / 'frontend/src/main/resources/sounds'
CACHE.mkdir(parents=True, exist_ok=True)
SESSION = requests.Session()
SESSION.headers.update({'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
                        'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36',
                        'Accept': '*/*'})


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.urls = []

    def handle_starttag(self, tag, attrs):
        if tag == 'a':
            self.urls.extend(v for k, v in attrs if k == 'href' and v)


def download(url, name, max_bytes=None):
    target = CACHE / name
    if not target.exists():
        headers = {}
        if 'scottbuckley.com.au' in url:
            headers['Referer'] = 'https://www.scottbuckley.com.au/library/'
        if max_bytes:
            headers['Range'] = 'bytes=0-' + str(max_bytes - 1)
        # A previously interrupted full download may already contain the excerpt.
        existing = CACHE / name.replace('-excerpt', '')
        partial_source = existing.with_suffix(existing.suffix + '.part')
        if max_bytes and partial_source.exists() and partial_source.stat().st_size >= max_bytes:
            with partial_source.open('rb') as source:
                target.write_bytes(source.read(max_bytes))
            return target
        response = SESSION.get(url, headers=headers, stream=True, timeout=(15, 40))
        response.raise_for_status()
        partial = target.with_suffix(target.suffix + '.part')
        size = 0
        with partial.open('wb') as output:
            for chunk in response.iter_content(256 * 1024):
                if max_bytes:
                    chunk = chunk[:max_bytes - size]
                output.write(chunk)
                size += len(chunk)
                if max_bytes and size >= max_bytes:
                    break
        response.close()
        partial.replace(target)
        print('Downloaded', name, size, 'bytes', flush=True)
    return target


def ffmpeg_executable():
    if os.environ.get('FFMPEG'):
        return os.environ['FFMPEG']
    # Download the platform wheel only; run its bundled converter, never its code.
    package = SESSION.get('https://pypi.org/pypi/imageio-ffmpeg/json', timeout=30)
    package.raise_for_status()
    wheel = next(x for x in package.json()['urls'] if 'win_amd64' in x['filename'])
    archive = download(wheel['url'], wheel['filename'])
    if hashlib.sha256(archive.read_bytes()).hexdigest() != wheel['digests']['sha256']:
        raise ValueError('FFmpeg wheel checksum mismatch')
    with zipfile.ZipFile(archive) as z:
        entry = next(n for n in z.namelist() if n.endswith('.exe') and 'ffmpeg' in n)
        target = CACHE / Path(entry).name
        target.write_bytes(z.read(entry))
    return str(target)


def pack(page, name):
    response = SESSION.get(page, timeout=40)
    response.raise_for_status()
    (CACHE / (name + '-source.html')).write_text(response.text, encoding='utf-8')
    parser = Links()
    parser.feed(response.text)
    urls = [u for u in parser.urls if u.endswith('.zip')]
    if urls:
        url = requests.compat.urljoin(page, urls[0])
    else:
        token = re.search(r"data-download-id=['\"]([^'\"]+)", response.text).group(1)
        signed = SESSION.get('https://kenney.nl/download-link/' + token,
                             headers={'Referer': page}, timeout=40)
        signed.raise_for_status()
        url = signed.json()['url']
    archive = download(url, name + '.zip')
    folder = CACHE / name
    folder.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        # Only unpack files beneath the intended folder.
        for info in z.infolist():
            dest = (folder / info.filename).resolve()
            if not dest.is_relative_to(folder.resolve()):
                raise ValueError('Unsafe ZIP member')
        z.extractall(folder)
    return folder


def main():
    import numpy as np
    from scipy.io import wavfile
    converter = ffmpeg_executable()
    folders = {name: pack('https://kenney.nl/assets/' + name, name)
               for name in ['interface-sounds', 'ui-audio', 'impact-sounds', 'music-jingles']}
    sources = {}

    def add(name, source, page, author, license='CC0-1.0', loop=False, seconds=None):
        # Decode every format before editing. WAV files have no codec dependency at runtime.
        originals = source if isinstance(source, list) else [source]
        data = None
        for index, original in enumerate(originals):
            decoded = CACHE / (name + '-' + str(index) + '-decoded.wav')
            command = [converter, '-hide_banner', '-loglevel', 'error', '-y', '-i', str(original)]
            if seconds:
                command += ['-t', str(seconds + 2)]
            subprocess.run(command + ['-ar', '44100', '-ac', '2' if loop else '1',
                                      '-c:a', 'pcm_s16le', str(decoded)], check=True)
            rate, pcm = wavfile.read(decoded)
            part = pcm.astype(np.float64) / 32768
            if data is None:
                data = part
            else:
                overlap = min(rate // 4, len(data) // 4, len(part) // 4)
                ramp = np.linspace(0, 1, overlap)[:, None]
                joint = data[-overlap:] * (1 - ramp) + part[:overlap] * ramp
                data = np.concatenate([data[:-overlap], joint, part[overlap:]])
        if not loop:
            # Preserve impact tails while removing source leading/trailing digital silence.
            active = np.flatnonzero(np.abs(data) > .001)
            if len(active):
                data = data[max(0, active[0] - 220):min(len(data), active[-1] + 2206)]
            edge = min(110, len(data) // 8)
            data[:edge] *= np.linspace(0, 1, edge)
            data[-edge:] *= np.linspace(1, 0, edge)
        else:
            # Circular overlap makes both sides of the file seam continuous.
            overlap = min(rate * 2, len(data) // 4)
            ramp = np.linspace(0, 1, overlap)[:, None]
            cross = data[-overlap:] * (1 - ramp) + data[:overlap] * ramp
            data = np.concatenate([cross, data[overlap:-overlap]])
        peak = np.max(np.abs(data))
        if peak < .0001:
            raise ValueError('Silent source: ' + str(source))
        # Leave mix headroom; per-bus playback gain supplies the remaining attenuation.
        data *= .78 / peak
        destination = OUTPUT / (name + '.wav')
        # Ensure even the shortest UI taps retain enough tail for portable mixers.
        if not loop and len(data) < 4410:
            data = np.pad(data, (0, 4410 - len(data)))
        wavfile.write(destination, rate, np.rint(data * 32767).astype(np.int16))
        sources[name + '.wav'] = dict(source_file=' + '.join(p.name for p in originals), source_page=page, author=author,
                                     license=license, loop=loop, seconds=round(len(data) / rate, 3),
                                     sha256=hashlib.sha256(destination.read_bytes()).hexdigest(),
                                     edits='PCM16 44.1kHz; peak level; fades/trim' + ('; circular crossfade' if loop else ''))
        print('Prepared', name, sources[name + '.wav']['seconds'], flush=True)

    def kenney(name, bundle, filename):
        source = next(p for p in folders[bundle].rglob('*') if p.name == filename)
        add(name, source, 'https://kenney.nl/assets/' + bundle, 'Kenney')

    interface = {
        'click': 'click_003.ogg', 'ready': 'confirmation_002.ogg',
        'ui_back': 'back_001.ogg', 'ui_panel_open': 'open_001.ogg', 'ui_panel_close': 'close_001.ogg',
        'ui_error': 'error_004.ogg', 'ship_remove': 'drop_002.ogg', 'fleet_random': 'confirmation_001.ogg',
        'turn_yours': 'confirmation_004.ogg', 'turn_enemy': 'select_003.ogg',
        'target_select': 'select_001.ogg', 'timer_warning': 'question_003.ogg', 'timer_timeout': 'error_003.ogg',
        'chat_receive': 'pluck_001.ogg', 'invite_receive': 'question_002.ogg',
        'room_join': 'confirmation_003.ogg', 'room_leave': 'back_002.ogg',
        'connection_lost': 'error_005.ogg', 'rematch_request': 'question_001.ogg',
    }
    for name, filename in interface.items():
        kenney(name, 'interface-sounds', filename)
    kenney('ui_hover', 'ui-audio', 'rollover2.ogg')
    kenney('ship_rotate', 'ui-audio', 'switch3.ogg')
    kenney('place', 'impact-sounds', 'impactWood_light_000.ogg')
    for name, filename in [('victory', 'jingles_PIZZI00.ogg'), ('defeat', 'jingles_PIZZI01.ogg'),
                           ('match_start', 'jingles_PIZZI02.ogg')]:
        kenney(name, 'music-jingles', filename)

    page = 'https://opengameart.org/content/battle-at-sea'
    response = SESSION.get(page, timeout=40)
    response.raise_for_status()
    (CACHE / 'battle-at-sea-source.html').write_text(response.text, encoding='utf-8')
    parser = Links(); parser.feed(response.text)
    for name, original in [('cannon', 'cannon_fire'), ('hit', 'cannon_hit_ship_short'),
                            ('splash', 'cannon_miss'), ('sunk', 'ship_destroyed_short')]:
        url = next(u for u in parser.urls if '/files/' in u and Path(u).name.startswith(original)
                   and u.endswith('.ogg'))
        source = download(url, Path(url).name)
        add(name, source, page, 'Thimras')
        sources[name + '.wav']['download_url'] = url

    music = [
        ('bgm_menu', 'https://www.scottbuckley.com.au/library/wp-content/uploads/2020/04/sb_signaltonoise.mp3',
         'https://www.scottbuckley.com.au/library/signal-to-noise/', 'Scott Buckley', 'CC-BY-4.0', 64),
        ('bgm_placement', 'https://www.scottbuckley.com.au/library/wp-content/uploads/2019/01/sb_intervention_nomelody.mp3',
         'https://www.scottbuckley.com.au/library/intervention/', 'Scott Buckley', 'CC-BY-4.0', 64),
        ('bgm_battle', 'https://opengameart.org/sites/default/files/esa.ogg',
         'https://opengameart.org/content/enemy-ship-approaching', 'yd', 'CC0-1.0', None),
        ('amb_ocean', 'https://opengameart.org/sites/default/files/wave_01_cc0-18363__jasinski__alkaibeach.flac',
         'https://opengameart.org/content/beach-ocean-waves', 'jasinski / qubodup', 'CC0-1.0', None),
        ('amb_wind', 'https://opengameart.org/sites/default/files/wind1.wav',
         'https://opengameart.org/content/wind1', 'Luke.RUSTLTD', 'CC0-1.0', None),
    ]
    for name, url, page, author, license, seconds in music:
        source = download(url, Path(url).stem + '-excerpt.mp3', 3 * 1024 * 1024) if seconds else download(url, Path(url).name)
        if name == 'amb_ocean':
            source = [source] + [download(url.replace('wave_01', 'wave_0' + str(i)),
                                         Path(url).name.replace('wave_01', 'wave_0' + str(i))) for i in range(2, 5)]
        add(name, source, page, author, license, True, seconds)
        sources[name + '.wav']['download_url'] = url
        if seconds:
            sources[name + '.wav']['source_file'] = Path(url).name
    (OUTPUT / 'manifest.json').write_text(json.dumps(sources, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    licenses = OUTPUT / 'licenses'
    licenses.mkdir(exist_ok=True)
    for bundle, folder in folders.items():
        (licenses / (bundle + '.txt')).write_bytes((folder / 'License.txt').read_bytes())
    for name, url in [
        ('CC-BY-4.0.txt', 'https://creativecommons.org/licenses/by/4.0/legalcode.txt'),
        ('CC0-1.0.txt', 'https://creativecommons.org/publicdomain/zero/1.0/legalcode.txt'),
    ]:
        (licenses / name).write_bytes(download(url, name).read_bytes())


if __name__ == '__main__':
    main()

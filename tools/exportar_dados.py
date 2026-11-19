#!/usr/bin/env python3
"""Gera fotografias de classificações usando a API oficial executada localmente."""
import json
import os
from pathlib import Path
import sys
import time
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'site' / 'dados'
BASE = 'http://127.0.0.1:8080/api/calculadora/dados-abertos/classificacoes-tributarias/cbs-ibs'
COMMIT = '150cccbe414d4a5edea703e4f7a424af5aa222c7'


def read(date):
    with urlopen(f'{BASE}?data={date}', timeout=60) as response:
        result = json.load(response)
    if not isinstance(result, list) or not result:
        raise ValueError('A API oficial não retornou uma lista de classificações.')
    if not any(item.get('codigo') == '000001' for item in result if isinstance(item, dict)):
        raise ValueError('A lista da Receita não contém a classificação esperada de controle.')
    return result


def main():
    deadline = time.monotonic() + 300
    while True:
        try:
            data_2027 = read('2027-01-01')
            break
        except (ConnectionError, OSError, HTTPError, URLError, ValueError) as error:
            if time.monotonic() >= deadline:
                raise RuntimeError('A calculadora oficial não ficou disponível a tempo.') from error
            time.sleep(3)
    DEST.mkdir(parents=True, exist_ok=True)
    for year, records in [(2027, data_2027), (2028, read('2028-01-01'))]:
        payload = {
            'data_referencia': f'{year}-01-01',
            'gerado_em': datetime.now(timezone.utc).isoformat(),
            'commit_receita': COMMIT,
            'fonte': 'https://github.com/ReceitaFederal/calculadora-rtc-regime-regular',
            'classificacoes': records,
        }
        (DEST / f'{year}.json').write_text(json.dumps(payload, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
        print(f'{year}: {len(records)} classificações exportadas')


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print(f'Não foi possível gerar a base oficial: {exc}', file=sys.stderr)
        sys.exit(1)

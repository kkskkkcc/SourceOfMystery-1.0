"""Audit and install the ten-second regeneration change without launching Minecraft."""
from pathlib import Path
import hashlib,json,re,shutil,zipfile,subprocess,tempfile

ROOT=Path(__file__).resolve().parents[2]
BASE='657c999f209b7b23c94408b362a70d92858575d6686277f5623243a65069ecc1'
RELEASE=ROOT/'build/libs/sourceofmystery-1.0.0.jar'
BACKUPS=ROOT/'build/release-backups'
BACKUP=BACKUPS/('sourceofmystery-before-energy-regen-'+BASE+'.jar')
INSTANCES=[Path('F:/AAAAmc/versions')/name for name in ['Integrated MC','1.20.1-Forge_47.4.20']]
EXPECTED={'com/sourceofmystery/config/MysteryConfig.class',
          'com/sourceofmystery/energy/MysteryEnergy.class',
          'com/sourceofmystery/energy/MysteryEnergyEvents.class'}
RECOMPILED={'com/sourceofmystery/config/MysteryConfig$HudAnchor.class',
            'com/sourceofmystery/config/MysteryConfig$SatelliteTier.class'}

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    baseline=BACKUP if BACKUP.exists() else INSTANCES[0]/'mods/sourceofmystery-1.0.0.jar'
    assert sha(baseline)==BASE,'Installed baseline changed'
    with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(RELEASE) as new:
        names={n for n in old.namelist() if not n.endswith('/')}
        assert names=={n for n in new.namelist() if not n.endswith('/')}
        changed={n for n in names if old.read(n)!=new.read(n)}
        assert changed==EXPECTED|RECOMPILED,changed
        # The two unchanged nested types only move in the source file. Confirm their
        # executable bytecode/API without including debug source line numbers.
        javap=Path('C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/javap.exe')
        with tempfile.TemporaryDirectory(prefix='energy-release-') as temp:
            for name in RECOMPILED:
                dumps=[]
                for label,jar in [('old',old),('new',new)]:
                    file=Path(temp)/(label+'.class');file.write_bytes(jar.read(name))
                    dumps.append(subprocess.run([str(javap),'-p','-c','-s',str(file)],capture_output=True,check=True).stdout)
                assert dumps[0]==dumps[1],('Nested config code changed',name)
    # Validate both destinations before writing either one.
    for instance in INSTANCES:
        assert sha(instance/'mods/sourceofmystery-1.0.0.jar') in [BASE,sha(RELEASE)]
        config=instance/'config/sourceofmystery-common.toml'
        assert re.search(r'(?m)^\s*regenerationPerSecond\s*=',config.read_text(encoding='utf8'))
    BACKUPS.mkdir(parents=True,exist_ok=True)
    if not BACKUP.exists():shutil.copy2(baseline,BACKUP)
    assert sha(BACKUP)==BASE
    installed=[]
    for instance in INSTANCES:
        config=instance/'config/sourceofmystery-common.toml'
        config_backup=BACKUPS/(instance.name+'-common-before-energy-regen.toml')
        if not config_backup.exists():shutil.copy2(config,config_backup)
        text=config.read_bytes().decode('utf8');nl='\r\n' if '\r\n' in text else '\n'
        text=text.replace('每秒恢复能量上限的比例；0.01 为 1%，0 为关闭；恢复不提高上限',
            '每10秒恢复当前玩家能量上限的比例；0.01 为 1%，0 为关闭；恢复不提高上限'+nl+
            '\t#regenerationPerSecond 为保留兼容的旧键名，实际按每10秒生效')
        text=re.sub(r'(?m)^(\s*regenerationPerSecond\s*=\s*)[^\r\n]+',r'\g<1>0.01',text,count=1)
        config.write_bytes(text.encode('utf8'))
        target=instance/'mods/sourceofmystery-1.0.0.jar'
        shutil.copy2(RELEASE,target);assert sha(target)==sha(RELEASE)
        installed.append({'jar':str(target),'config':str(config),'config_backup':str(config_backup)})
    report={'interval_ticks':200,'interval_seconds_at_20_tps':10,'fraction_of_current_maximum':0.01,
        'fractional_remainder_preserved':True,'daily_refresh_unchanged':True,'legacy_config_key_retained':True,
        'sha256':sha(RELEASE),'bytes':RELEASE.stat().st_size,'baseline_sha256':BASE,
        'changed_entries':sorted(changed),'nested_config_types_bytecode_unchanged':True,'all_other_release_entries_unchanged':True,
        'game_tests_launched':False,'game_client_launched':False,'backup':str(BACKUP),'installed':installed}
    (ROOT/'build/energy-regeneration-release.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()

import io
from multiprocessing.connection import wait
import os
import re
import requests
import shutil
import zipfile

hardcoded_version_tag = None

url_repo = "https://github.com/cmorley191/MusicBot"
url_releases = f"{url_repo}/releases"
url_latest = f"{url_releases}/latest"
url_release_tag = f"{url_releases}/tag"
url_download = f"{url_releases}/download"

def flatten(l):
  return [item for sublist in l for item in sublist]

def waitForYesNo():
  entry = input().lower()
  if entry in ["y", "yes", "install"]:
    return True
  return False

def install(version_tag):
  url_version = f"{url_release_tag}/{version_tag}"
  print(f"Downloading {version_tag} from {url_version}")
  resp_version = requests.get(f"{url_release_tag}/{version_tag}", allow_redirects=True)
  assert resp_version.status_code == 200, f"Could not find release at {url_version}"
  
  jar_filenames = [
    f"jmusicbot-{version_tag.lower()}-all.jar",
    f"jmusicbot-{version_tag.lower()}-snapshot-all.jar",
    f"jmusicbot-{version_tag.lower()}.jar",
    f"jmusicbot-all.jar",
    f"jmusicbot-snapshot-all.jar",
  ]
  jar_filenames = [
    *[f"cmorley191-{x}" for x in jar_filenames],
    *jar_filenames,
  ]
  
  urls_version_download = [
    f"{url_download}/{version_tag}/cmorley191_jmusicbot_{version_tag}.zip",
    *[f"{url_download}/{version_tag}/{jar_filename}" for jar_filename in jar_filenames],
  ]
  for url_version_download in urls_version_download:
    resp_download = requests.get(url_version_download, allow_redirects=True)
    if resp_download.status_code != 200:
      continue
    print(f"Downloaded {url_version_download}")

    jar_content = resp_download.content
    if (url_version_download.lower().endswith(".zip")):
      zip_content = resp_download.content
      with io.BytesIO(zip_content) as zip_content_io:
        with zipfile.ZipFile(zip_content_io) as archive:
          jar_content = archive.read("JMusicBot-Snapshot-All.jar")
          print("Extracted zip.")
    
    print(f"Enter install file or folder path: (input nothing to install at current working directory)")
    path = input().strip()
    if path == "":
      path = os.getcwd()
    if os.path.isfile(path) or os.path.isdir(path):
      if os.path.isfile(path):
        dirname = os.path.dirname(path)
      else:
        dirname = path

      # check for config/serversettings files
      path_config = os.path.join(dirname, "config.txt")
      if os.path.isfile(path_config):
        print(f"Backing up config.txt to config.txt.backup")
        shutil.copy(path_config, f"{path_config}.backup")
      else:
        print(f"No config file found to backup.")
      path_serversettings = os.path.join(dirname, "serversettings.json")
      if os.path.isfile(path_serversettings):
        print(f"Backing up serversettings.json to serversettings.json.backup")
        shutil.copy(path_serversettings, f"{path_serversettings}.backup")
      else:
        print(f"No serversettings file found to backup.")

      if os.path.isfile(path):
        filename = os.path.basename(path)
        file_exists = True
      else:
        files = os.listdir(path)
        files_lower = [x.lower() for x in files]
        for jar_filename in jar_filenames:
          if jar_filename in files_lower:
            filename = files[files_lower.index(jar_filename)]
            file_exists = True
            break
        else:
          print(f"Executable jar not found in folder {path}")
          filename = jar_filenames[0]
          file_exists = False
        path = os.path.join(path, filename)
      print(f"{'Overwrite' if file_exists else 'Install'} executable jar at {path} ? (y/n)")
      if not waitForYesNo():
        print("Aborting")
        return

      if filename.lower() not in jar_filenames:
        print(f"Supplied path does not match a recognized cmorley191-JMusicBot.jar filename: {filename}")
        print(f"Are you sure you want to install the executable jar at this filename? (y/n)")
        if not waitForYesNo():
          print("Aborting")
          return

    else:
      print(f"Path does not exist: {path}")
      if path.lower().endswith(".jar"):
        print(f"Install executable jar at that filepath? (y/n)")
        if not waitForYesNo():
          print("Aborting")
          return
        os.makedirs(os.path.dirname(path))
      else:
        print(f"Create a new installation folder there? (y/n)")
        if not waitForYesNo():
          print("Aborting")
          return
        os.makedirs(path)
        path = os.path.join(path, jar_filenames[0])

    # path is now the executable filename to install at

    print(f"Backing up {os.path.basename(path)} to {os.path.basename(path)}.backup")
    shutil.copy(path, f"{path}.backup")

    print("Ready to install? (y/n)")
    if not waitForYesNo():
      print("Aborting")
      return
    with open(path, 'wb') as f:
      f.write(jar_content)
    print(f"Executable jar installed successfully.")
    break

  else:
    assert False, f"Could not find any automatically-installable release artifacts at {url_version}"


def main(hardcoded_version_tag):
  print("Checking latest version...")
  resp_latest = requests.get(url_latest, allow_redirects=True)
  assert resp_latest.history, f"Could not check latest release version at {url_latest}"
  resp_latest_tag = resp_latest
  url_latest_tag = resp_latest.url
  assert resp_latest_tag.status_code == 200, f"Could not check latest release version at {url_latest} -> {url_latest_tag}\n{resp_latest.history}"

  latest_version_tag_match = re.fullmatch(rf'{re.escape(f"{url_release_tag}/")}(.*)', url_latest_tag)
  assert latest_version_tag_match is not None and len(latest_version_tag_match.groups()) == 1, f"Could not find a release version tag at {url_latest} -> {url_latest_tag}"
  latest_version_tag = latest_version_tag_match.group(1)
  assert latest_version_tag.strip() != "", f"Could not find a valid release version tag at {url_latest} -> {url_latest_tag}"

  if hardcoded_version_tag is None or latest_version_tag == hardcoded_version_tag:
    print(f"Installer version is latest stable version: {latest_version_tag}")
  else:
    print(f"Installer version {hardcoded_version_tag} does not match latest stable version {latest_version_tag}")
  print(f"What would you like to do? Options:")
  if hardcoded_version_tag is not None and latest_version_tag != hardcoded_version_tag:
    print(f"Install installer version {hardcoded_version_tag} (I)")
  print(f"Install latest stable version {latest_version_tag} (L)")
  print(f"Enter other version to install (O)")
  print(f"Abort without installing (A)")
  entry = input().lower()
  if hardcoded_version_tag is not None and latest_version_tag != hardcoded_version_tag and (re.split(r'\s+', entry.lower()) in [
    ["i"],
    *flatten([[*x, *y, *z] for x in [[], ["instal"], ["install"]] for y in [["installer"], ["instaler"]] for z in [[], ["version"]]]),
  ]):
    install(hardcoded_version_tag)
  elif (re.split(r'\s+', entry.lower()) in [
    ["l"], ["s"], ["ls"],
    *flatten([[*x, *y, *z] for x in [[], ["instal"], ["install"]] for y in [["latest"], ["stable"], ["latest", "stable"]] for z in [[], ["version"]]]),
  ]):
    install(latest_version_tag)
  elif (re.split(r'\s+', entry.lower()) in [
    ["o"], ["enter"], ["enter", "version"],
    *flatten([[*x, *y, *z] for x in [[], ["enter"], ["install"], ["instal"]] for y in [["other"], ["another"]] for z in [[], ["version"]]]),
  ]):
    print("Enter version to install:")
    entry = input()
    if entry in ['1', '1.0', '1.0.0', '1.1', '1.1.0', '1.2', '1.2.0', '1.3_beta1', '1.3.0_beta1']:
      print(f"Entered version {entry} is not supported for automated install, sorry.")
    else:
      install(entry)
  else:
    print("Aborting")


if __name__ == '__main__':
  main(hardcoded_version_tag)
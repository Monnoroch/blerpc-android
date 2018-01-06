# Install shellcheck.

sudo apt-get update
sudo apt-get install -y --no-install-recommends shellcheck=0.3.*
sudo apt-get clean
sudo rm -rf /var/lib/apt/lists/*

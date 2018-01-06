# Install hadolint.

hadolint_path=linters/bin/hadolint
wget --output-document=${hadolint_path} --quiet https://github.com/lukasmartinelli/hadolint/releases/download/v1.2.1/hadolint_linux_amd64
echo "2c292d28122a75e5e9e138c6c9b21443 ${hadolint_path}" | md5sum -c
chmod +x "${hadolint_path}"

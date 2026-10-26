/** File types that can run code when opened on Windows. Downloads of these get an extra warning. */
const DANGEROUS_EXTENSIONS = new Set([
  'exe', 'msi', 'msix', 'msp', 'appx', 'appxbundle', 'bat', 'cmd', 'com', 'cpl', 'scr', 'pif',
  'ps1', 'psm1', 'vbs', 'vbe', 'js', 'jse', 'wsf', 'wsh', 'hta', 'jar', 'lnk', 'reg', 'dll',
  'sys', 'inf', 'gadget', 'application', 'msc', 'iso', 'img', 'vhd', 'vhdx', 'chm', 'url', 'scf',
  'xll', 'xlam', 'docm', 'xlsm', 'pptm', 'library-ms', 'settingcontent-ms',
]);

const RESERVED_WINDOWS_NAMES = /^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\..*)?$/i;

export function fileExtension(name: string): string {
  const base = name.split(/[\\/]/).pop() ?? '';
  const idx = base.lastIndexOf('.');
  return idx > 0 ? base.slice(idx + 1).toLowerCase() : '';
}

export function isDangerousDownload(name: string): boolean {
  return DANGEROUS_EXTENSIONS.has(fileExtension(name));
}

/** Makes a server-supplied filename safe to use as a default save name on Windows. */
export function sanitizeFilename(raw: string): string {
  let name = (raw.split(/[\\/]/).pop() ?? '')
    // eslint-disable-next-line no-control-regex
    .replace(/[\u0000-\u001f\u007f<>:"|?*]/g, '_')
    .replace(/[‪-‮⁦-⁩‎‏]/g, '') // bidi overrides used to disguise extensions
    .replace(/^[.\s]+|[.\s]+$/g, '')
    .trim();
  if (!name || RESERVED_WINDOWS_NAMES.test(name)) name = `download${name ? `_${name}` : ''}`;
  if (name.length > 180) {
    const ext = fileExtension(name);
    name = ext ? `${name.slice(0, 170)}.${ext}` : name.slice(0, 180);
  }
  return name;
}

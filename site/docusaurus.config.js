// @ts-check

const config = {
  title: 'Automorph',
  tagline: 'Simple and powerful RPC for Scala',
  url: 'https://automorph.org',
  baseUrl: '/',
  favicon: 'icon.png',
  onBrokenLinks: 'ignore',
  onBrokenMarkdownLinks: 'warn',
  organizationName: 'automorph',
  projectName: 'automorph',

  presets: [
    [
      'classic',
      ({
        blog: false,
        sitemap: false,
        googleAnalytics: false,
        gtag: false,
        docs: {
          path: process.env['SITE_DOCS'] ?? '../docs',
          sidebarPath: require.resolve('./src/components/Sidebar.js'),
        },
        theme: {
          customCss: require.resolve('./src/pages/custom.css'),
        },
      }),
    ],
  ],

  plugins: [
    '@someok/docusaurus-plugin-relative-paths',
  ],

  themes: [
    [
      require.resolve('@easyops-cn/docusaurus-search-local'),
      /** @type {import("@easyops-cn/docusaurus-search-local").PluginOptions} */
      ({
        explicitSearchResultPath: true,
        hashed: true,
        highlightSearchTermsOnTargetPage: true,
        searchResultContextMaxLength: 64,
        searchResultLimits: 12,
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      algolia: false,
      navbar: {
        title: '',
        logo: {
          alt: 'Automorph',
          src: 'logo.png',
        },
        items: [
          {
            to: 'https://automorph.org/docs/Overview',
            label: 'Documentation',
            position: 'left',
          },
          {
            to: 'https://automorph.org/api/automorph.html',
            label: 'API',
            position: 'left',
          },
          {
            to: 'https://github.com/automorph-org/automorph',
            label: 'Source',
            position: 'left',
          },
          {
            to: 'https://central.sonatype.com/namespace/org.automorph',
            label: 'Artifacts',
            position: 'left',
          },
          {
            to: 'mailto:automorph.org@proton.me',
            label: 'Contact',
            position: 'left',
          },
        ],
      },
      prism: {
        theme: require('prism-react-renderer/themes/okaidia'),
        darkTheme: require('prism-react-renderer/themes/okaidia'),
        additionalLanguages: ['java', 'scala']
      },
    }),
};

module.exports = config;


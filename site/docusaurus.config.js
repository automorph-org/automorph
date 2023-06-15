// @ts-check

const config = {
  title: 'Automorph',
  tagline: 'RPC in a single line of Scala code',
  url: 'https://automorph.org',
  baseUrl: '/',
  favicon: 'icon.jpg',
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
          src: 'icon.jpg',
        },
        items: [
          {
            href: '/docs/Overview',
            label: 'Documentation',
            position: 'left',
          },
          {
            href: '/api/index.html',
            label: 'API',
            position: 'left',
          },
          {
            href: 'https://github.com/automorph-org/automorph',
            label: 'Source',
            position: 'left',
          },
          {
            href: 'https://mvnrepository.com/artifact/org.automorph/automorph',
            label: 'Artifacts',
            position: 'left',
          },
          {
            href: 'mailto:automorph.org@proton.me',
            label: 'Contact',
            position: 'left',
          },
        ],
      },
      prism: {
        theme: require('prism-react-renderer/themes/nightOwl'),
        darkTheme: require('prism-react-renderer/themes/nightOwl'),
        additionalLanguages: ['java', 'scala']
      },
    }),
};

module.exports = config;


"""Keep repository-source links working in the generated documentation site."""
import posixpath
import re


def on_page_markdown(markdown, page, config, files):
    def source_link(match):
        target = posixpath.normpath(posixpath.join('docs', posixpath.dirname(page.file.src_uri), match.group(1)))
        if not target.startswith('docs/'):
            return '](' + 'https://github.com/honestTai/rent-project/blob/main/' + target + ')'
        return match.group(0)
    markdown = re.sub(r'\]\((\.\./[^)\s]+)\)', source_link, markdown)
    # Keep screenshots readable at their original resolution on the hosted site.
    return re.sub(
        r'!\[([^\]]*)\]\(([^)\s]*assets/screenshots/[^)\s]+)\)',
        r'[![\1](\2)](\2)',
        markdown,
    )

import assert from 'node:assert/strict';
import {test} from 'node:test';
import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
import {mkdtempSync,writeFileSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname,join,resolve} from 'node:path';
import {runInNewContext} from 'node:vm';

const require = createRequire(new URL('../frontend/package.json', import.meta.url));

test('the generator keeps its JavaScript, HTML and CSS formatting APIs', () => {
    const beautifier = require('js-beautify');
    const source = '(()=>{const values=[1,2,3];return values.reduce((a,b)=>a+b,0)})()';
    assert.equal(runInNewContext(beautifier.js(source)), 6);
    assert.match(beautifier.html('<section><b>value</b></section>'), /<b>value<\/b>/);
    assert.match(beautifier.css('a{color:red}'), /color: red/);
});

test('the formatter CLI still expands files and applies editorconfig', () => {
    const directory = mkdtempSync(join(tmpdir(), 'metal-formatter-'));
    try {
        writeFileSync(join(directory, '.editorconfig'), 'root = true\n[*.js]\nindent_style = space\nindent_size = 2\n');
        writeFileSync(join(directory, 'sample.js'), 'function sample(){return 1;}');
        execFileSync(process.execPath, [require.resolve('js-beautify/js/bin/js-beautify.js'), '--editorconfig', '--replace', '*.js'], {cwd: directory});
        assert.match(readFileSync(join(directory, 'sample.js'), 'utf8'), /\n {2}return 1;/);
    } finally {
        assert.equal(dirname(resolve(directory)), resolve(tmpdir()));
        rmSync(directory, {recursive: true, force: true});
    }
});

test('SVG icons retain scoped styles when the parser dependencies change', async () => {
    const Compiler = require('svg-baker');
    const compiler = new Compiler();
    const symbol = await compiler.addSymbol({path: 'sample.svg', id: 'icon-sample',
        content: '<svg viewBox="0 0 10 10"><style>.shape { fill: red; }</style><path class="shape" d="M0 0h10v10z"/></svg>'});
    const output = symbol.render();
    assert.match(output, /<symbol[^>]*id="icon-sample"/);
    assert.match(output, /#icon-sample .shape/);
    assert.match(output, /fill: red/);
    assert.match(output, /class="shape"/);
});

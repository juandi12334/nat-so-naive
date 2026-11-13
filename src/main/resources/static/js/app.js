/*
 * Not So Naive simulator - front-end logic.
 * The browser never searches the text: it uploads the file and the pattern to the
 * Java backend and only paints the positions returned by Not So Naive.
 */
(function () {
    'use strict';

    const MAX_BYTES = parseInt(document.body.dataset.maxBytes || '2097152', 10);
    const PAGE_SIZE = 200;

    const $ = (id) => document.getElementById(id);
    const els = {
        dropzone: $('dropzone'), fileInput: $('fileInput'), pickBtn: $('pickBtn'), changeBtn: $('changeBtn'),
        fileCard: $('fileCard'), fileName: $('fileName'), fileSize: $('fileSize'), fileError: $('fileError'),
        pattern: $('pattern'), patternShown: $('patternShown'), patternLen: $('patternLen'), patternError: $('patternError'),
        searchBtn: $('searchBtn'), statusPill: $('statusPill'), statusText: $('statusText'),
        resultsEmpty: $('resultsEmpty'), processing: $('processing'), errorBox: $('errorBox'), errorText: $('errorText'),
        results: $('results'), matchCount: $('matchCount'), notices: $('notices'),
        positions: $('positions'), posPager: $('posPager'), posPrev: $('posPrev'), posNext: $('posNext'), posPageInfo: $('posPageInfo'),
        previewSection: $('previewSection'), preview: $('preview'), previewNote: $('previewNote'),
        prevMatch: $('prevMatch'), nextMatch: $('nextMatch'), matchNav: $('matchNav'),
        traceSection: $('traceSection'), traceBody: $('traceBody'),
        pdfSection: $('pdfSection'), pdfBtn: $('pdfBtn'), pdfLink: $('pdfLink'), pdfStatus: $('pdfStatus')
    };

    const state = {file: null, last: null, lastPattern: null, page: 0, current: -1, pdfUrl: null, busy: false};

    $('maxSize').textContent = formatBytes(MAX_BYTES);

    // ---------- status ----------
    function setStatus(kind, text) {
        els.statusPill.className = 'pill pill--' + kind;
        els.statusText.textContent = text;
    }

    function formatBytes(b) {
        if (b < 1024) return b + ' B';
        if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
        return (b / (1024 * 1024)).toFixed(2) + ' MB';
    }

    const fmt = (n) => Number(n).toLocaleString('en-US');

    // ---------- file selection ----------
    function showFileError(msg) {
        els.fileError.textContent = msg;
        els.fileError.hidden = !msg;
    }

    function acceptFile(file) {
        showFileError('');
        if (!file) return;
        if (!/\.txt$/i.test(file.name)) {
            showFileError('Only .txt files are accepted. "' + file.name + '" is not a .txt file.');
            return;
        }
        if (file.size === 0) {
            showFileError('The file "' + file.name + '" is empty.');
            return;
        }
        if (file.size > MAX_BYTES) {
            showFileError('The file is too large (' + formatBytes(file.size) + '). Maximum: ' + formatBytes(MAX_BYTES) + '.');
            return;
        }
        state.file = file;
        els.fileName.textContent = file.name;
        els.fileSize.textContent = formatBytes(file.size) + ' · ' + fmt(file.size) + ' bytes';
        els.fileCard.hidden = false;
        els.dropzone.hidden = true;
        resetResults();
        setStatus('loaded', 'File loaded: ' + file.name);
        updateButton();
    }

    function openPicker() {
        els.fileInput.value = '';
        els.fileInput.click();
    }

    els.pickBtn.addEventListener('click', (e) => { e.stopPropagation(); openPicker(); });
    els.dropzone.addEventListener('click', openPicker);
    els.dropzone.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPicker(); }
    });
    els.changeBtn.addEventListener('click', openPicker);
    els.fileInput.addEventListener('change', () => acceptFile(els.fileInput.files[0]));

    ['dragenter', 'dragover'].forEach((ev) => els.dropzone.addEventListener(ev, (e) => {
        e.preventDefault(); els.dropzone.classList.add('is-over');
    }));
    ['dragleave', 'drop'].forEach((ev) => els.dropzone.addEventListener(ev, (e) => {
        e.preventDefault(); els.dropzone.classList.remove('is-over');
    }));
    els.dropzone.addEventListener('drop', (e) => {
        const files = e.dataTransfer && e.dataTransfer.files;
        if (files && files.length > 1) { showFileError('Drop a single .txt file.'); return; }
        acceptFile(files && files[0]);
    });
    // allow dropping anywhere on the card area after a file is loaded
    document.addEventListener('dragover', (e) => e.preventDefault());
    document.addEventListener('drop', (e) => {
        e.preventDefault();
        if (e.target.closest && e.target.closest('#dropzone')) return;
        const files = e.dataTransfer && e.dataTransfer.files;
        if (files && files.length === 1) acceptFile(files[0]);
    });

    // ---------- pattern ----------
    function patternProblem(p) {
        if (p.length === 0) return 'The pattern must not be empty.';
        for (let i = 0; i < p.length; i++) {
            const c = p.charCodeAt(i);
            if (c < 0x20 || c > 0x7e) return 'Only printable ASCII characters are allowed (character ' + (i + 1) + ' is not).';
        }
        return '';
    }

    function renderPattern() {
        const p = els.pattern.value;
        if (p.length === 0) {
            els.patternShown.textContent = '—';
            els.patternShown.classList.add('is-empty');
            els.patternLen.textContent = '';
        } else {
            els.patternShown.textContent = p.replace(/ /g, '·');
            els.patternShown.classList.remove('is-empty');
            els.patternLen.textContent = 'm = ' + p.length + (p.includes(' ') ? ' (· = space)' : '');
        }
        const problem = p.length ? patternProblem(p) : '';
        els.patternError.textContent = problem;
        els.patternError.hidden = !problem;
        els.pattern.classList.toggle('is-invalid', !!problem);
        updateButton();
    }

    els.pattern.addEventListener('input', renderPattern);
    els.pattern.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !els.searchBtn.disabled) runSearch();
    });

    function updateButton() {
        const p = els.pattern.value;
        els.searchBtn.disabled = state.busy || !state.file || patternProblem(p) !== '';
    }

    // ---------- search ----------
    function resetResults() {
        state.last = null;
        els.results.hidden = true;
        els.errorBox.hidden = true;
        els.processing.hidden = true;
        els.resultsEmpty.hidden = false;
        els.previewSection.hidden = true;
        els.traceSection.hidden = true;
        els.pdfSection.hidden = true;
        clearPdf();
    }

    function clearPdf() {
        if (state.pdfUrl) URL.revokeObjectURL(state.pdfUrl);
        state.pdfUrl = null;
        els.pdfLink.hidden = true;
        els.pdfStatus.textContent = '';
    }

    function setBusy(btn, busy) {
        state.busy = busy;
        btn.classList.toggle('is-loading', busy);
        btn.disabled = busy;
        if (!busy) updateButton();
    }

    function formData() {
        const fd = new FormData();
        fd.append('file', state.file, state.file.name);
        fd.append('pattern', state.lastPattern);
        return fd;
    }

    async function readError(res) {
        try {
            const body = await res.json();
            if (body && body.error) return body.error;
        } catch (_) { /* not JSON */ }
        return 'The server answered with status ' + res.status + '.';
    }

    async function runSearch() {
        const p = els.pattern.value;
        const problem = patternProblem(p);
        if (problem) { els.patternError.textContent = problem; els.patternError.hidden = false; return; }
        if (!state.file) { showFileError('Choose a .txt file first.'); return; }

        state.lastPattern = p;
        resetResults();
        els.resultsEmpty.hidden = true;
        els.processing.hidden = false;
        setStatus('busy', 'Searching…');
        setBusy(els.searchBtn, true);
        try {
            const res = await fetch('/api/search', {method: 'POST', body: formData()});
            if (!res.ok) throw new Error(await readError(res));
            const data = await res.json();
            state.last = data;
            renderResults(data);
            setStatus('done', 'Search completed · ' + fmt(data.matchCount) + ' occurrence' + (data.matchCount === 1 ? '' : 's'));
        } catch (err) {
            els.processing.hidden = true;
            els.errorText.textContent = err.message || String(err);
            els.errorBox.hidden = false;
            setStatus('error', 'Processing error');
        } finally {
            setBusy(els.searchBtn, false);
        }
    }

    els.searchBtn.addEventListener('click', runSearch);

    function renderResults(d) {
        els.processing.hidden = true;
        els.results.hidden = false;
        els.matchCount.textContent = fmt(d.matchCount);
        $('rPattern').textContent = d.pattern;
        $('rFile').textContent = d.fileName + ' (' + formatBytes(d.fileSizeBytes) + ', ' + fmt(d.lineCount) + ' lines)';
        $('rTime').textContent = d.elapsedMillis.toFixed(3) + ' ms';
        $('rN').textContent = fmt(d.textLength);
        $('rM').textContent = fmt(d.patternLength);
        $('rKell').textContent = d.patternLength === 1 ? '1 / 1 (m = 1)' : d.k + ' / ' + d.ell;
        $('rAttempts').textContent = fmt(d.attempts);
        $('rComparisons').textContent = fmt(d.comparisons);

        els.notices.innerHTML = '';
        const notes = (d.notices || []).slice();
        if (d.positionsTruncated) notes.push('Showing the first ' + fmt(d.positions.length) + ' positions; the count and the PDF include all ' + fmt(d.matchCount) + '.');
        notes.forEach((n) => { const li = document.createElement('li'); li.textContent = n; els.notices.appendChild(li); });

        state.page = 0;
        state.current = -1;
        renderPositions();
        renderPreview(d);
        renderTrace(d);
        els.pdfSection.hidden = false;
    }

    function renderPositions() {
        const d = state.last;
        const list = d.positions;
        els.positions.innerHTML = '';
        if (list.length === 0) {
            els.positions.innerHTML = '<span class="none">No occurrences: the pattern does not appear in the text.</span>';
            els.posPager.hidden = true;
            return;
        }
        const pages = Math.ceil(list.length / PAGE_SIZE);
        const from = state.page * PAGE_SIZE;
        const to = Math.min(list.length, from + PAGE_SIZE);
        const frag = document.createDocumentFragment();
        for (let i = from; i < to; i++) {
            const b = document.createElement('button');
            b.type = 'button';
            b.textContent = list[i];
            b.dataset.idx = i;
            b.title = 'Occurrence ' + (i + 1) + ' starts at position ' + list[i];
            if (i === state.current) b.classList.add('is-current');
            frag.appendChild(b);
        }
        els.positions.appendChild(frag);
        els.posPager.hidden = pages <= 1;
        els.posPageInfo.textContent = 'Page ' + (state.page + 1) + ' of ' + pages;
        els.posPrev.disabled = state.page === 0;
        els.posNext.disabled = state.page >= pages - 1;
    }

    els.positions.addEventListener('click', (e) => {
        const b = e.target.closest('button[data-idx]');
        if (b) focusMatch(parseInt(b.dataset.idx, 10));
    });
    els.posPrev.addEventListener('click', () => { state.page--; renderPositions(); });
    els.posNext.addEventListener('click', () => { state.page++; renderPositions(); });

    // ---------- highlighted preview ----------
    function escapeHtml(s) {
        return s.replace(/[&<>]/g, (c) => (c === '&' ? '&amp;' : c === '<' ? '&lt;' : '&gt;'));
    }

    /*
     * Builds the preview from the server positions only. Boundaries are placed at every
     * occurrence start/end; each segment is painted according to how many occurrences
     * cover it (1 = match, 2+ = overlap). Each occurrence start gets an anchor id.
     */
    function renderPreview(d) {
        const text = d.text;
        const n = text.length;
        const m = d.patternLength;
        const pos = d.positions.filter((p) => p < n);
        const delta = new Map();
        const starts = new Map();
        const add = (k, v) => delta.set(k, (delta.get(k) || 0) + v);
        pos.forEach((p, i) => {
            add(p, 1);
            add(Math.min(n, p + m), -1);
            if (!starts.has(p)) starts.set(p, i);
        });
        const bounds = Array.from(new Set([0, n, ...delta.keys()])).sort((a, b) => a - b);
        let depth = 0;
        const out = [];
        for (let b = 0; b < bounds.length - 1; b++) {
            const a = bounds[b];
            depth += delta.get(a) || 0;
            const seg = escapeHtml(text.slice(a, bounds[b + 1]));
            const anchor = starts.has(a) ? ' id="m' + starts.get(a) + '"' : '';
            if (depth > 0) {
                out.push('<mark class="' + (depth > 1 ? 'd2' : 'd1') + '"' + anchor + '>' + seg + '</mark>');
            } else {
                out.push(seg);
            }
        }
        els.preview.innerHTML = out.join('');
        els.previewSection.hidden = false;
        els.previewNote.hidden = !d.previewTruncated;
        if (d.previewTruncated) {
            els.previewNote.textContent = 'Preview limited to the first ' + fmt(n) + ' characters of ' + fmt(d.textLength)
                + '. The count, the positions and the PDF cover the whole file.';
        }
        updateMatchNav();
    }

    function updateMatchNav() {
        const total = state.last ? state.last.positions.length : 0;
        els.matchNav.textContent = total === 0 ? '0 / 0' : (state.current < 0 ? '–' : state.current + 1) + ' / ' + fmt(total);
        els.prevMatch.disabled = total === 0;
        els.nextMatch.disabled = total === 0;
    }

    function focusMatch(i) {
        const total = state.last.positions.length;
        if (total === 0) return;
        state.current = (i + total) % total;
        const page = Math.floor(state.current / PAGE_SIZE);
        if (page !== state.page) { state.page = page; }
        renderPositions();
        els.preview.querySelectorAll('mark.is-current').forEach((el) => el.classList.remove('is-current'));
        const el = document.getElementById('m' + state.current);
        if (el) {
            el.classList.add('is-current');
            const top = el.offsetTop - els.preview.offsetTop - els.preview.clientHeight / 3;
            els.preview.scrollTo({top: Math.max(0, top), behavior: 'smooth'});
        }
        updateMatchNav();
    }

    els.prevMatch.addEventListener('click', () => focusMatch(state.current < 0 ? -1 : state.current - 1));
    els.nextMatch.addEventListener('click', () => focusMatch(state.current + 1));

    // ---------- trace ----------
    const OUTCOME_TEXT = {
        MATCH: 'Occurrence reported',
        MISMATCH_SECOND: 'x[1] ≠ y[j+1] → shift k',
        MISMATCH_REST: 'x[1] ok, x[2..m−1] differs → shift ell',
        MISMATCH_FIRST: 'x[1..m−1] ok, x[0] ≠ y[j] → shift ell'
    };

    function renderTrace(d) {
        if (!d.trace) { els.traceSection.hidden = true; return; }
        const rows = d.trace.map((s, i) => {
            const win = escapeHtml(d.text.substr(s.j, d.patternLength)).replace(/\n/g, '↵').replace(/ /g, '·');
            let outcome = OUTCOME_TEXT[s.outcome];
            if (d.patternLength === 1) outcome = s.outcome === 'MATCH' ? 'Occurrence reported (m = 1)' : 'x[0] ≠ y[j] → shift 1';
            return '<tr><td>' + (i + 1) + '</td><td>' + s.j + '</td><td class="win">' + win + '</td><td><span class="tag tag--'
                + s.outcome + '">' + outcome + '</span></td><td>' + s.comparisons + '</td><td>+' + s.shift + '</td></tr>';
        });
        els.traceBody.innerHTML = rows.join('');
        els.traceSection.hidden = false;
    }

    // ---------- PDF ----------
    els.pdfBtn.addEventListener('click', async () => {
        if (!state.file || !state.last) return;
        clearPdf();
        setBusy(els.pdfBtn, true);
        setStatus('busy', 'Generating PDF…');
        els.pdfStatus.textContent = 'Generating the PDF on the server with Apache PDFBox…';
        try {
            const res = await fetch('/api/pdf', {method: 'POST', body: formData()});
            if (!res.ok) throw new Error(await readError(res));
            const blob = await res.blob();
            const name = 'not-so-naive-' + state.file.name.replace(/\.txt$/i, '').replace(/[^A-Za-z0-9._-]/g, '_') + '.pdf';
            state.pdfUrl = URL.createObjectURL(blob);
            els.pdfLink.href = state.pdfUrl;
            els.pdfLink.download = name;
            els.pdfLink.hidden = false;
            els.pdfLink.click();
            const count = res.headers.get('X-Match-Count');
            els.pdfStatus.textContent = 'PDF ready (' + formatBytes(blob.size) + ') with ' + fmt(count) + ' highlighted occurrence'
                + (count === '1' ? '' : 's') + '. If the download did not start, use "Download again".';
            setStatus('done', 'PDF generated');
        } catch (err) {
            els.pdfStatus.textContent = 'Could not generate the PDF: ' + (err.message || err);
            setStatus('error', 'PDF error');
        } finally {
            setBusy(els.pdfBtn, false);
            els.pdfBtn.disabled = false;
        }
    });

    renderPattern();
})();

import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';

// ─── State ────────────────────────────────────────────────────────────────────

const state = {
  modelLoaded: false,
  inferring: false,
  systemInfo: null,
  currentMsgEl: null,
  unlisten: null,
  chatHistory: [],
  loadedModelId: null,
};

// Model compatibility data (full VRAM | AirLLM min VRAM)
const MODELS = [
  { name: 'Llama 2 7B',   fullVram: 14,  airVram: 4  },
  { name: 'Llama 2 13B',  fullVram: 26,  airVram: 4  },
  { name: 'Llama 2 70B',  fullVram: 140, airVram: 4  },
  { name: 'Mistral 7B',   fullVram: 14,  airVram: 4  },
  { name: 'Gemma 7B',     fullVram: 14,  airVram: 4  },
  { name: 'Qwen2 7B',     fullVram: 14,  airVram: 4  },
];

// ─── Tab switching ────────────────────────────────────────────────────────────

window.switchTab = function(name) {
  document.querySelectorAll('.tab').forEach(t => { t.classList.remove('active'); t.setAttribute('aria-selected','false'); });
  document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
  document.getElementById(`tab-${name}`).classList.add('active');
  document.getElementById(`tab-${name}`).setAttribute('aria-selected','true');
  document.getElementById(`panel-${name}`).classList.add('active');
};

// ─── Device Analysis ─────────────────────────────────────────────────────────

async function loadSystemInfo() {
  try {
    const info = await invoke('get_system_info');
    state.systemInfo = info;

    document.getElementById('info-cpu').textContent = info.cpu || 'Unknown';
    document.getElementById('info-ram').textContent = info.ram_gb
      ? `${info.ram_gb.toFixed(1)} GB`
      : 'Unknown';

    const gpus = info.gpu || [];
    if (gpus.length > 0) {
      document.getElementById('info-gpu').textContent =
        gpus.map(g => `${g.name} (${g.vram})`).join(', ');
    } else {
      document.getElementById('info-gpu').textContent = 'No dedicated GPU';
    }

    document.getElementById('info-python').textContent = info.python || 'Not found';

    // Detect first GPU VRAM for compat table
    const vramMatch = gpus[0]?.vram?.match(/(\d+)/);
    const detectedVram = vramMatch ? parseInt(vramMatch[1]) * (gpus[0].vram.includes('MiB') ? 1/1024 : 1) : 0;
    renderCompatTable(detectedVram);
  } catch (e) {
    console.error('system info error:', e);
  }
}

async function checkAirllm() {
  const checkEl = document.getElementById('airllm-check');
  const iconEl  = document.getElementById('install-icon');
  const titleEl = document.getElementById('install-title');
  const descEl  = document.getElementById('install-desc');
  const btnEl   = document.getElementById('btn-install');

  try {
    const installed = await invoke('check_airllm_installed');
    if (installed) {
      checkEl.classList.add('ok');
      iconEl.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20,6 9,17 4,12"/></svg>`;
      iconEl.classList.add('ok');
      titleEl.textContent = 'AirLLM is installed ✓';
      descEl.textContent  = 'Python package ready';
    } else {
      checkEl.classList.add('error');
      iconEl.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`;
      iconEl.classList.add('error');
      titleEl.textContent = 'AirLLM not installed';
      descEl.textContent  = 'Click install to set up the Python package';
      btnEl.style.display = 'flex';
    }
  } catch(e) {
    descEl.textContent = 'Could not check — is Python installed?';
  }
}

function renderCompatTable(detectedVramGB) {
  const tbody = document.getElementById('compat-tbody');
  tbody.innerHTML = '';

  MODELS.forEach(m => {
    const saving = Math.round((1 - m.airVram / m.fullVram) * 100);
    const canRun = detectedVramGB >= m.airVram;
    const status = detectedVramGB === 0
      ? `<span class="badge-warn">CPU (slow)</span>`
      : canRun
        ? `<span class="badge-ok">✓ Compatible</span>`
        : `<span class="badge-no">✗ Need ${m.airVram} GB</span>`;

    tbody.insertAdjacentHTML('beforeend', `
      <tr>
        <td><strong>${m.name}</strong></td>
        <td>${m.fullVram} GB</td>
        <td>${m.airVram} GB min</td>
        <td><span class="saving-pct">↓${saving}%</span></td>
        <td>${status}</td>
      </tr>
    `);
  });
}

window.installAirllm = async function() {
  const logEl  = document.getElementById('install-log');
  const btnEl  = document.getElementById('btn-install');
  const descEl = document.getElementById('install-desc');
  logEl.style.display = 'block';
  logEl.textContent   = '';
  btnEl.disabled = true;
  descEl.textContent  = 'Installing…';

  const unlistenInstall = await listen('install-output', (event) => {
    const data = event.payload;
    if (data.type === 'done') {
      descEl.textContent = 'Installation complete! Reload to verify.';
      unlistenInstall();
      checkAirllm();
    } else if (data.text) {
      logEl.textContent += data.text + '\n';
      logEl.scrollTop = logEl.scrollHeight;
    }
  });

  await invoke('install_airllm');
};

// ─── VRAM Chart ───────────────────────────────────────────────────────────────

function renderVramChart() {
  const chart = document.getElementById('vram-chart');
  const maxVram = 140; // Llama 70B

  chart.innerHTML = MODELS.map(m => {
    const stdPct    = Math.min((m.fullVram / maxVram) * 100, 100);
    const airPct    = Math.min((m.airVram  / maxVram) * 100, 100);
    const saving    = Math.round((1 - m.airVram / m.fullVram) * 100);
    return `
      <div class="vram-row">
        <div class="vram-model">${m.name}</div>
        <div class="vram-bars">
          <div class="vram-bar-wrap">
            <div class="vram-bar standard" style="width:${stdPct}%">${m.fullVram < 30 ? '' : m.fullVram + ' GB'}</div>
            <span class="vram-bar-label" style="${m.fullVram < 30 ? '' : 'display:none'}">${m.fullVram} GB</span>
          </div>
          <div class="vram-bar-wrap">
            <div class="vram-bar airllm" style="width:${airPct}%"></div>
            <span class="vram-bar-label">${m.airVram} GB &nbsp;<strong style="color:var(--lime)">↓${saving}%</strong></span>
          </div>
        </div>
      </div>
    `;
  }).join('');
}

// ─── Model / Chat ─────────────────────────────────────────────────────────────

document.getElementById('model-select').addEventListener('change', function() {
  const customSection = document.getElementById('custom-model-section');
  customSection.style.display = this.value === 'custom' ? 'flex' : 'none';
});

window.loadModel = async function() {
  const selectEl = document.getElementById('model-select');
  const modelId  = selectEl.value === 'custom'
    ? document.getElementById('custom-model-input').value.trim()
    : selectEl.value;

  if (!modelId) return;

  const compressionRatio = parseInt(document.getElementById('compression-ratio').value, 10);

  setModelStatus('loading', `Loading ${modelId.split('/').pop()}…`);
  setStatusBadge('running', 'Loading model…');
  document.getElementById('btn-load').disabled = true;

  try {
    await invoke('preload_model', {
      modelName: modelId,
      compressionRatio,
    });

    state.modelLoaded = true;
    state.loadedModelId = modelId;
    state.chatHistory = [];

    setModelStatus('ready', `${modelId.split('/').pop()} ready`);
    setStatusBadge('ready', 'Model ready');
    document.getElementById('btn-send').disabled = false;

    const welcome = document.querySelector('.chat-welcome');
    if (welcome) welcome.remove();

    document.getElementById('chat-messages').innerHTML = '';
    addSystemMessage(`Model ${modelId} loaded via AirLLM. Starting chat…`);
  } catch (e) {
    state.modelLoaded = false;
    state.loadedModelId = null;
    setModelStatus('idle', 'Load failed');
    setStatusBadge('ready', 'Load failed');
    addSystemMessage(`Failed to load model: ${e}`);
  } finally {
    document.getElementById('btn-load').disabled = false;
  }
};

window.stopInference = async function() {
  await invoke('stop_inference');
  if (state.unlisten) { state.unlisten(); state.unlisten = null; }
  state.inferring = false;
  setModelStatus('ready', 'Stopped');
  setStatusBadge('ready', 'Stopped');
  document.getElementById('btn-stop').disabled = true;
  document.getElementById('btn-send').disabled = false;

  // Remove streaming cursor
  if (state.currentMsgEl) {
    const cursor = state.currentMsgEl.querySelector('.cursor');
    if (cursor) cursor.remove();
    state.currentMsgEl = null;
  }
};

window.handleChatKey = function(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
};

window.autoResize = function(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 160) + 'px';
};

function buildPrompt(userMessage) {
  const history = state.chatHistory.slice(-10);
  const lines = history.map(turn => {
    const role = turn.role === 'user' ? 'User' : 'Assistant';
    return `${role}: ${turn.content}`;
  });
  lines.push(`User: ${userMessage}`);
  lines.push('Assistant:');
  return lines.join('\n');
}

window.sendMessage = async function() {
  if (!state.modelLoaded || state.inferring) return;

  const inputEl = document.getElementById('chat-input');
  const prompt  = inputEl.value.trim();
  if (!prompt) return;

  inputEl.value = '';
  inputEl.style.height = 'auto';

  const modelId = state.loadedModelId || (
    document.getElementById('model-select').value === 'custom'
      ? document.getElementById('custom-model-input').value.trim()
      : document.getElementById('model-select').value
  );

  const maxTokens = parseInt(document.getElementById('max-tokens').value, 10);
  const compressionRatio = parseInt(document.getElementById('compression-ratio').value, 10);
  const fullPrompt = buildPrompt(prompt);

  // Add user bubble
  addMessage('user', prompt);

  // Add empty model bubble with cursor
  const modelBubble = addMessage('model', '');
  modelBubble.innerHTML = '<span class="cursor"></span>';
  state.currentMsgEl = modelBubble;

  state.inferring = true;
  document.getElementById('btn-send').disabled = true;
  document.getElementById('btn-stop').disabled = false;
  setModelStatus('running', 'Generating…');
  setStatusBadge('running', 'Generating…');

  let assistantText = '';

  // Listen for streamed tokens
  state.unlisten = await listen('inference-output', (event) => {
    const data = event.payload;
    if (!state.currentMsgEl) return;

    if (data.type === 'token' && data.text) {
      assistantText += data.text;
      const cursor = state.currentMsgEl.querySelector('.cursor');
      if (cursor) {
        cursor.insertAdjacentText('beforebegin', data.text);
      } else {
        state.currentMsgEl.textContent += data.text;
      }
      scrollChat();
    } else if (data.type === 'done' || data.type === 'error') {
      const cursor = state.currentMsgEl?.querySelector('.cursor');
      if (cursor) cursor.remove();
      if (data.type === 'error') {
        state.currentMsgEl.textContent += `\n[Error: ${data.text}]`;
        state.currentMsgEl.style.color = 'var(--red)';
        finishInference(prompt, '', true);
      } else {
        finishInference(prompt, assistantText, false);
      }
    }
  });

  try {
    await invoke('run_inference', {
      modelName: modelId,
      prompt: fullPrompt,
      maxTokens,
      compressionRatio,
    });
  } catch (e) {
    if (state.currentMsgEl) {
      state.currentMsgEl.textContent = `Error: ${e}`;
      state.currentMsgEl.style.color = 'var(--red)';
    }
    finishInference(prompt, '', true);
  }
};

function finishInference(userPrompt, assistantText, isError) {
  if (!isError && userPrompt && assistantText) {
    state.chatHistory.push({ role: 'user', content: userPrompt });
    state.chatHistory.push({ role: 'assistant', content: assistantText });
  } else if (!isError && userPrompt) {
    state.chatHistory.push({ role: 'user', content: userPrompt });
  }
  state.inferring = false;
  state.currentMsgEl = null;
  if (state.unlisten) { state.unlisten(); state.unlisten = null; }
  document.getElementById('btn-send').disabled = false;
  document.getElementById('btn-stop').disabled = true;
  setModelStatus('ready', 'Ready');
  setStatusBadge('ready', 'Ready');
}

// ─── DOM helpers ─────────────────────────────────────────────────────────────

function addMessage(role, text) {
  const msgsEl = document.getElementById('chat-messages');
  const initials = role === 'user' ? 'U' : 'AI';
  const div = document.createElement('div');
  div.className = `msg ${role}`;
  div.innerHTML = `
    <div class="msg-avatar">${initials}</div>
    <div class="msg-bubble"></div>
  `;
  const bubble = div.querySelector('.msg-bubble');
  bubble.textContent = text;
  msgsEl.appendChild(div);
  scrollChat();
  return bubble;
}

function addSystemMessage(text) {
  const msgsEl = document.getElementById('chat-messages');
  const div = document.createElement('div');
  div.style.cssText = 'text-align:center;font-size:12px;color:var(--text-dim);padding:4px 0;';
  div.textContent = text.replace(/\*\*(.*?)\*\*/g, '$1');
  msgsEl.appendChild(div);
}

function scrollChat() {
  const msgsEl = document.getElementById('chat-messages');
  msgsEl.scrollTop = msgsEl.scrollHeight;
}

function setModelStatus(cls, text) {
  const ind = document.getElementById('model-status-indicator');
  ind.className = `status-indicator ${cls}`;
  document.getElementById('model-status-text').textContent = text;
}

function setStatusBadge(cls, text) {
  const dot = document.querySelector('.status-dot');
  dot.className = `status-dot ${cls}`;
  document.getElementById('status-text').textContent = text;
}

// ─── Init ─────────────────────────────────────────────────────────────────────

document.getElementById('btn-stop').disabled  = true;
document.getElementById('btn-send').disabled  = true;

loadSystemInfo();
checkAirllm();
renderVramChart();

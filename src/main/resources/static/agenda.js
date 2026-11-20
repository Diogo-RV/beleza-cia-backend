(() => {
    const mesAno = document.getElementById('mesAno');
    const diasContainer = document.getElementById('dias');
    const dataTexto = document.getElementById('dataTexto');
    const diaTexto = document.getElementById('diaTexto');
    const listaHorarios = document.getElementById('listaHorarios');
    const servicoSelect = document.getElementById('servicoSelect');
    const confirmacaoBox = document.getElementById('confirmacaoBox');
    const resumoEscolha = document.getElementById('resumoEscolha');
    const mensagemAgendamento = document.getElementById('mensagemAgendamento');
    const avisoAgenda = document.getElementById('avisoAgenda');
    const areaAgendamento = document.getElementById('areaAgendamento');
    const listaAgendamentos = document.getElementById('agendamentosLista');
    const alternarEncerradosClienteBtn = document.getElementById('alternarEncerradosClienteBtn');
    const descricaoAgendamentosCliente = document.getElementById('descricaoAgendamentosCliente');
    const selecaoServico = document.getElementById('selecaoServico');
    const painelAdminBloqueios = document.getElementById('painelAdminBloqueios');
    const adminWorkspace = document.getElementById('adminWorkspace');
    const adminColuna = document.getElementById('adminColuna');
    const adminAgendamentosDia = document.getElementById('adminAgendamentosDia');
    const contagemConfirmados = document.getElementById('contagemConfirmados');
    const contagemCancelados = document.getElementById('contagemCancelados');
    const listaBloqueiosDia = document.getElementById('listaBloqueiosDia');
    const adminDataSelecionada = document.getElementById('adminDataSelecionada');
    const mensagemBloqueio = document.getElementById('mensagemBloqueio');
    const bloqueioInicio = document.getElementById('bloqueioInicio');
    const bloqueioFim = document.getElementById('bloqueioFim');
    const bloqueioMotivo = document.getElementById('bloqueioMotivo');
    const listaServicosAdmin = document.getElementById('listaServicosAdmin');
    const servicoAdminForm = document.getElementById('servicoAdminForm');
    const mensagemServicoAdmin = document.getElementById('mensagemServicoAdmin');
    const painelHistoricoAdmin = document.getElementById('painelHistoricoAdmin');
    const listaHistoricoAdmin = document.getElementById('listaHistoricoAdmin');
    const resumoHistoricoAdmin = document.getElementById('resumoHistoricoAdmin');
    const historicoStatusFiltro = document.getElementById('historicoStatusFiltro');
    const historicoInicioFiltro = document.getElementById('historicoInicioFiltro');
    const historicoFimFiltro = document.getElementById('historicoFimFiltro');
    const historicoPaginaInfo = document.getElementById('historicoPaginaInfo');
    const historicoAnteriorBtn = document.getElementById('historicoAnteriorBtn');
    const historicoProximoBtn = document.getElementById('historicoProximoBtn');

    const nomesMeses = [
        'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
        'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'
    ];
    const nomesDias = [
        'Domingo', 'Segunda-feira', 'Terça-feira', 'Quarta-feira',
        'Quinta-feira', 'Sexta-feira', 'Sábado'
    ];
    const horariosPossiveis = [
        '08:00', '08:30', '09:00', '09:30', '10:00', '10:30',
        '11:00', '11:30', '13:00', '13:30', '14:00', '14:30',
        '15:00', '15:30', '16:00', '16:30'
    ];

    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0);
    let mesExibido = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
    let dataSelecionada = null;
    let horarioSelecionado = null;
    let usuarioAtual = null;
    let servicos = [];
    let intervalosOcupados = [];
    let bloqueiosAdmin = [];
    let diasBloqueados = [];
    let agendamentosCarregados = [];
    let filtroAdmin = 'confirmados';
    let servicoEditandoId = null;
    let atualizacaoAdminEmAndamento = false;
    let historicoPaginaAtual = 0;
    let historicoTotalPaginas = 0;
    let historicoAberto = false;
    let historicoCarregando = false;
    let mostrarEncerradosCliente = false;

    async function obterTokenCsrf() {
        await verificarIdentidadeDaSessao();
        const resposta = await fetch('/api/auth/csrf', {
            credentials: 'same-origin',
            cache: 'no-store'
        });
        if (!resposta.ok) throw new Error('Não foi possível validar a sessão de segurança.');
        const dados = await resposta.json();
        return dados.token;
    }

    async function verificarIdentidadeDaSessao() {
        if (!usuarioAtual) return;
        const resposta = await fetch('/api/auth/me', {
            method: 'GET',
            credentials: 'same-origin',
            cache: 'no-store'
        });

        if (!resposta.ok) {
            window.location.replace('/index.html');
            throw new Error('Sua sessão terminou. Entre novamente.');
        }

        const usuarioDaSessao = await resposta.json();
        const identidadeMudou = Number(usuarioDaSessao.id) !== Number(usuarioAtual.id)
            || usuarioDaSessao.perfil !== usuarioAtual.perfil;

        if (identidadeMudou) {
            // O navegador compartilha cookies entre abas do mesmo perfil. Se outra aba
            // entrou com uma conta diferente, recarrega para não misturar telas/identidades.
            window.location.replace('/agenda.html');
            throw new Error('A conta ativa neste navegador mudou. A página será atualizada para mostrar o usuário correto.');
        }
    }

    async function api(url, opcoes = {}) {
        if (url !== '/api/auth/me') {
            await verificarIdentidadeDaSessao();
        }
        const resposta = await fetch(url, {
            credentials: 'same-origin',
            ...opcoes,
            cache: 'no-store',
            headers: {
                ...(opcoes.headers || {})
            }
        });

        if (resposta.status === 401 || resposta.status === 403 && url.endsWith('/api/auth/me')) {
            window.location.href = '/index.html';
            throw new Error('Sua sessão expirou. Entre novamente.');
        }

        if (!resposta.ok) {
            let mensagem = `Erro HTTP ${resposta.status}.`;
            try {
                const dados = await resposta.json();
                mensagem = dados.erro || dados.message || mensagem;
            } catch (_) { /* resposta sem JSON */ }
            throw new Error(mensagem);
        }

        if (resposta.status === 204) return null;
        return resposta.json();
    }

    function escaparHtml(valor) {
        return String(valor ?? '').replace(/[&<>"']/g, caractere => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        })[caractere]);
    }

    function dataIso(data) {
        const ano = data.getFullYear();
        const mes = String(data.getMonth() + 1).padStart(2, '0');
        const dia = String(data.getDate()).padStart(2, '0');
        return `${ano}-${mes}-${dia}`;
    }

    function exibirData(data) {
        return data.toLocaleDateString('pt-BR', {
            day: '2-digit', month: 'long', year: 'numeric'
        });
    }

    function dataHoraLocal(valor) {
        // O backend envia LocalDateTime no formato yyyy-MM-ddTHH:mm:ss.
        return new Date(valor);
    }

    function servicoSelecionado() {
        return servicos.find(servico => String(servico.id) === servicoSelect.value) || null;
    }

    function limparSelecaoHorario() {
        horarioSelecionado = null;
        confirmacaoBox.hidden = true;
        resumoEscolha.textContent = '';
    }

    async function carregarUsuario() {
        usuarioAtual = await api('/api/auth/me');
        const tipoUsuario = usuarioAtual.perfil === 'ADMIN' ? 'Administradora' : 'Cliente';
        document.getElementById('boasVindas').textContent = `Olá, ${usuarioAtual.nome} · ${tipoUsuario}`;

        if (usuarioAtual.perfil === 'ADMIN') {
            // O painel administrativo reúne calendário, atendimentos do dia e bloqueios.
            document.body.classList.add('admin-mode');
            adminWorkspace.classList.add('admin-workspace-admin');
            adminColuna.hidden = false;
            document.getElementById('legendaAdmin').hidden = false;
            selecaoServico.hidden = true;
            document.getElementById('painelClienteHorarios').hidden = true;
            document.getElementById('painelAgendamentosCliente').hidden = true;
            document.getElementById('tituloCalendario').textContent = 'Calendário da agenda';
            document.getElementById('instrucaoCalendario').textContent = 'Selecione uma data para ver os atendimentos e administrar a disponibilidade';
            document.getElementById('mensagemCalendario').hidden = true;
            avisoAgenda.textContent = 'Selecione uma data. Os atendimentos e os bloqueios daquele dia aparecerão ao lado do calendário.';
        }
    }

    async function carregarServicos() {
        servicos = await api('/api/servicos');
        servicoSelect.innerHTML = '<option value="">Selecione um serviço</option>';

        servicos.forEach(servico => {
            const option = document.createElement('option');
            option.value = servico.id;
            const preco = Number(servico.preco).toLocaleString('pt-BR', {
                style: 'currency', currency: 'BRL'
            });
            option.textContent = `${servico.nome} — ${preco} (${servico.duracao_minutos} min)`;
            servicoSelect.appendChild(option);
        });
    }

    function renderizarServicosAdmin() {
        if (!listaServicosAdmin) return;
        if (!servicos.length) {
            listaServicosAdmin.innerHTML = '<p class="admin-vazio">Nenhum serviço cadastrado.</p>';
            return;
        }

        listaServicosAdmin.innerHTML = servicos.map(servico => {
            const preco = Number(servico.preco).toLocaleString('pt-BR', {
                style: 'currency', currency: 'BRL'
            });
            return `
                <article class="admin-servico-item">
                    <div class="admin-servico-dados">
                        <strong>${escaparHtml(servico.nome)}</strong>
                        <span>${escaparHtml(preco)} · ${Number(servico.duracao_minutos)} min</span>
                    </div>
                    <div class="admin-servico-acoes">
                        <button type="button" class="botao-servico-editar" data-editar-servico="${Number(servico.id)}">Editar</button>
                        <button type="button" class="botao-servico-excluir" data-excluir-servico="${Number(servico.id)}">Excluir</button>
                    </div>
                </article>
            `;
        }).join('');
    }

    function abrirFormServico(servico = null) {
        servicoEditandoId = servico ? Number(servico.id) : null;
        document.getElementById('tituloFormServico').textContent = servico ? 'Editar serviço' : 'Novo serviço';
        document.getElementById('servicoAdminNome').value = servico?.nome || '';
        document.getElementById('servicoAdminPreco').value = servico ? Number(servico.preco).toFixed(2) : '';
        document.getElementById('servicoAdminDuracao').value = servico?.duracao_minutos || 30;
        document.getElementById('salvarServicoBtn').textContent = servico ? 'Salvar alterações' : 'Cadastrar serviço';
        mensagemServicoAdmin.textContent = '';
        servicoAdminForm.hidden = false;
        document.getElementById('servicoAdminNome').focus();
    }

    function fecharFormServico() {
        servicoEditandoId = null;
        servicoAdminForm.reset();
        servicoAdminForm.hidden = true;
        mensagemServicoAdmin.textContent = '';
    }

    async function salvarServicoAdmin(evento) {
        evento.preventDefault();
        const nome = document.getElementById('servicoAdminNome').value.trim();
        const preco = Number(document.getElementById('servicoAdminPreco').value);
        const duracaoMinutos = Number(document.getElementById('servicoAdminDuracao').value);
        if (!nome || !Number.isFinite(preco) || preco <= 0 || !Number.isInteger(duracaoMinutos) || duracaoMinutos < 1) {
            mensagemServicoAdmin.textContent = 'Informe nome, preço maior que zero e duração válida.';
            return;
        }

        const botao = document.getElementById('salvarServicoBtn');
        botao.disabled = true;
        mensagemServicoAdmin.textContent = 'Salvando serviço...';
        try {
            const token = await obterTokenCsrf();
            const payload = { nome, preco, duracaoMinutos };
            const edicao = servicoEditandoId !== null;
            await api(edicao ? `/api/servicos/${servicoEditandoId}` : '/api/servicos', {
                method: edicao ? 'PUT' : 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': token
                },
                body: JSON.stringify(payload)
            });
            fecharFormServico();
            await carregarServicos();
            renderizarServicosAdmin();
            mensagemServicoAdmin.textContent = edicao ? 'Serviço atualizado com sucesso.' : 'Serviço cadastrado com sucesso.';
        } catch (erro) {
            mensagemServicoAdmin.textContent = erro.message;
        } finally {
            botao.disabled = false;
        }
    }

    async function excluirServicoAdmin(id) {
        const servico = servicos.find(item => Number(item.id) === Number(id));
        if (!servico) return;
        if (!window.confirm(`Deseja excluir o serviço "${servico.nome}"? Serviços ligados a agendamentos não podem ser excluídos.`)) return;
        try {
            const token = await obterTokenCsrf();
            await api(`/api/servicos/${id}`, {
                method: 'DELETE',
                headers: { 'X-XSRF-TOKEN': token }
            });
            await carregarServicos();
            renderizarServicosAdmin();
            mensagemServicoAdmin.textContent = 'Serviço excluído.';
            if (servicoEditandoId === Number(id)) fecharFormServico();
        } catch (erro) {
            mensagemServicoAdmin.textContent = erro.message;
        }
    }

    function renderizarCalendario() {
        diasContainer.innerHTML = '';
        mesAno.textContent = `${nomesMeses[mesExibido.getMonth()]} ${mesExibido.getFullYear()}`;

        const primeiroDiaSemana = new Date(
            mesExibido.getFullYear(), mesExibido.getMonth(), 1
        ).getDay();
        const quantidadeDias = new Date(
            mesExibido.getFullYear(), mesExibido.getMonth() + 1, 0
        ).getDate();

        for (let i = 0; i < primeiroDiaSemana; i++) {
            const vazio = document.createElement('span');
            vazio.className = 'dia-vazio';
            diasContainer.appendChild(vazio);
        }

        for (let numeroDia = 1; numeroDia <= quantidadeDias; numeroDia++) {
            const data = new Date(
                mesExibido.getFullYear(), mesExibido.getMonth(), numeroDia
            );
            const botao = document.createElement('button');
            botao.type = 'button';
            botao.className = 'dia';
            botao.textContent = numeroDia;

            const admin = usuarioAtual && usuarioAtual.perfil === 'ADMIN';
            const domingo = data.getDay() === 0;
            const passado = data < hoje;
            const diaBloqueadoParaCliente = usuarioAtual &&
                usuarioAtual.perfil !== 'ADMIN' && diasBloqueados.includes(dataIso(data));
            // A cliente não seleciona domingos/datas passadas; Mary pode consultar o histórico.
            if ((!admin && (domingo || passado)) || diaBloqueadoParaCliente) {
                botao.disabled = true;
                botao.classList.add('indisponivel');
                if (domingo) botao.title = 'Não há atendimento aos domingos';
                else if (passado) botao.title = 'Data passada';
                else botao.title = 'Dia bloqueado pela administradora';
            }

            if (admin) {
                const agendamentosDoDia = agendamentosCarregados.filter(item =>
                    String(item.data_hora || '').slice(0, 10) === dataIso(data)
                );
                if (agendamentosDoDia.some(item => String(item.status || '').toLowerCase() === 'confirmado')) {
                    botao.classList.add('dia-com-atendimento');
                    botao.title = 'Possui atendimento(s) confirmado(s)';
                }
                if (agendamentosDoDia.some(item => String(item.status || '').toLowerCase() === 'concluido')) {
                    botao.classList.add('dia-com-concluido');
                    botao.title = (botao.title ? botao.title + ' e ' : '') + 'possui atendimento(s) concluído(s)';
                }
                if (agendamentosDoDia.some(item => String(item.status || '').toLowerCase() === 'cancelado')) {
                    botao.classList.add('dia-com-cancelamento');
                    botao.title += ' e atendimento(s) cancelado(s)';
                }
            }

            if (dataSelecionada && dataIso(data) === dataIso(dataSelecionada)) {
                botao.classList.add('selecionado');
            }

            if (usuarioAtual && usuarioAtual.perfil === 'ADMIN') {
                const blocosDoDia = bloqueiosAdmin.filter(bloco =>
                    String(bloco.data_inicio).slice(0, 10) === dataIso(data)
                );
                if (blocosDoDia.length > 0) {
                    botao.classList.add('dia-com-bloqueio');
                    botao.title = 'Este dia possui bloqueio de agenda';
                    const bloqueioIntegral = blocosDoDia.some(bloco =>
                        String(bloco.data_inicio).slice(11, 16) <= '08:00' &&
                        String(bloco.data_fim).slice(11, 16) >= '17:00'
                    );
                    if (bloqueioIntegral) botao.classList.add('dia-bloqueado-integral');
                }
            }

            if ((admin || (!domingo && !passado)) && !diaBloqueadoParaCliente) {
                botao.addEventListener('click', async () => {
                    dataSelecionada = data;
                    limparSelecaoHorario();
                    dataTexto.textContent = exibirData(dataSelecionada);
                    diaTexto.textContent = nomesDias[dataSelecionada.getDay()];
                    renderizarCalendario();

                    if (usuarioAtual.perfil === 'ADMIN') {
                        mensagemBloqueio.textContent = '';
                        painelAdminBloqueios.hidden = false;
                        renderizarPainelAdminDia();
                        await renderizarPainelAdminBloqueios();
                    } else {
                        await carregarHorarios();
                    }
                });
            }

            diasContainer.appendChild(botao);
        }

        const totalCelulas = diasContainer.children.length;
        for (let i = totalCelulas; i < 42; i++) {
            const vazio = document.createElement('span');
            vazio.className = 'dia-vazio';
            diasContainer.appendChild(vazio);
        }

        const limiteMesAnterior = usuarioAtual && usuarioAtual.perfil === 'ADMIN'
            ? new Date(hoje.getFullYear() - 1, hoje.getMonth(), 1)
            : new Date(hoje.getFullYear(), hoje.getMonth(), 1);
        document.getElementById('mesAnterior').disabled = mesExibido <= limiteMesAnterior;
    }


    function formatarHora(valor) {
        return String(valor || '').slice(11, 16);
    }

    async function carregarDiasBloqueados() {
        if (!dataIso) return;
        const primeiroDia = new Date(mesExibido.getFullYear(), mesExibido.getMonth(), 1);
        const ultimoDia = new Date(mesExibido.getFullYear(), mesExibido.getMonth() + 1, 0);
        const inicio = dataIso(primeiroDia);
        const fim = dataIso(ultimoDia);
        diasBloqueados = await api(
            `/api/agendamentos/dias-bloqueados?inicio=${encodeURIComponent(inicio)}&fim=${encodeURIComponent(fim)}`
        );
    }

    async function carregarBloqueiosAdmin() {
        const primeiroDia = new Date(mesExibido.getFullYear(), mesExibido.getMonth(), 1);
        const ultimoDia = new Date(mesExibido.getFullYear(), mesExibido.getMonth() + 1, 0);
        const parametros = new URLSearchParams({
            inicio: dataIso(primeiroDia),
            fim: dataIso(ultimoDia)
        });
        bloqueiosAdmin = await api(`/api/bloqueios?${parametros.toString()}`);
        renderizarCalendario();
        if (dataSelecionada) await renderizarPainelAdminBloqueios();
    }

    async function renderizarPainelAdminBloqueios() {
        if (!dataSelecionada) {
            painelAdminBloqueios.hidden = true;
            return;
        }

        painelAdminBloqueios.hidden = false;
        adminDataSelecionada.textContent = exibirData(dataSelecionada);
        const dataPassada = dataSelecionada < hoje;
        document.getElementById('bloquearIntervaloBtn').disabled = dataPassada;
        document.getElementById('bloquearDiaBtn').disabled = dataPassada;
        bloqueioInicio.disabled = dataPassada;
        bloqueioFim.disabled = dataPassada;
        bloqueioMotivo.disabled = dataPassada;
        if (dataPassada) {
            mensagemBloqueio.textContent = 'Data passada: você pode consultar os registros, mas não criar novos bloqueios.';
        } else if (mensagemBloqueio.textContent.startsWith('Data passada:')) {
            mensagemBloqueio.textContent = '';
        }
        const data = dataIso(dataSelecionada);
        const blocos = bloqueiosAdmin.filter(bloco =>
            String(bloco.data_inicio).slice(0, 10) === data
        ).sort((a, b) => String(a.data_inicio).localeCompare(String(b.data_inicio)));

        if (blocos.length === 0) {
            listaBloqueiosDia.innerHTML = '<p>Não há bloqueios cadastrados para esta data.</p>';
            return;
        }

        listaBloqueiosDia.innerHTML = blocos.map(bloco => `
            <article class="card-bloqueio">
                <div>
                    <strong>${escaparHtml(formatarHora(bloco.data_inicio))} às ${escaparHtml(formatarHora(bloco.data_fim))}</strong>
                    <span>${escaparHtml(bloco.motivo || 'Bloqueio sem motivo informado')}</span>
                </div>
                <button type="button" class="botao-liberar" data-excluir-bloqueio="${Number(bloco.id)}">Liberar</button>
            </article>
        `).join('');
    }

    async function salvarBloqueio(inicio, fim) {
        if (!dataSelecionada) {
            mensagemBloqueio.textContent = 'Selecione uma data no calendário.';
            return;
        }

        const data = dataIso(dataSelecionada);
        const motivo = bloqueioMotivo.value.trim();
        const botaoIntervalo = document.getElementById('bloquearIntervaloBtn');
        const botaoDia = document.getElementById('bloquearDiaBtn');
        botaoIntervalo.disabled = true;
        botaoDia.disabled = true;
        mensagemBloqueio.textContent = 'Salvando bloqueio...';

        try {
            const token = await obterTokenCsrf();
            await api('/api/bloqueios', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': token
                },
                body: JSON.stringify({
                    dataInicio: `${data}T${inicio}:00`,
                    dataFim: `${data}T${fim}:00`,
                    motivo
                })
            });

            mensagemBloqueio.textContent = 'Bloqueio cadastrado com sucesso.';
            await carregarBloqueiosAdmin();
        } catch (erro) {
            mensagemBloqueio.textContent = erro.message;
        } finally {
            const dataPassada = dataSelecionada && dataSelecionada < hoje;
            botaoIntervalo.disabled = Boolean(dataPassada);
            botaoDia.disabled = Boolean(dataPassada);
        }
    }

    async function excluirBloqueio(id) {
        if (!window.confirm('Deseja liberar este horário e permitir novos agendamentos?')) return;
        try {
            const token = await obterTokenCsrf();
            await api(`/api/bloqueios/${id}`, {
                method: 'DELETE',
                headers: { 'X-XSRF-TOKEN': token }
            });
            mensagemBloqueio.textContent = 'Bloqueio removido. O horário voltou a ficar disponível, se não houver outro impedimento.';
            await carregarBloqueiosAdmin();
        } catch (erro) {
            mensagemBloqueio.textContent = erro.message;
        }
    }

    async function buscarIntervalosOcupados() {
        if (!dataSelecionada) return [];
        const data = dataIso(dataSelecionada);
        return api(`/api/agendamentos/ocupados?data=${encodeURIComponent(data)}`);
    }

    function horarioDentroDoExpediente(inicio, fim, horario) {
        const limiteIso = `${dataIso(dataSelecionada)}T${horario < '12:00' ? '12:00:00' : '17:00:00'}`;
        const limite = new Date(limiteIso);
        return fim <= limite;
    }

    function conflitaComIntervalo(inicio, fim, intervalo) {
        const ocupadoInicio = dataHoraLocal(intervalo.inicio);
        const ocupadoFim = dataHoraLocal(intervalo.fim);
        return inicio < ocupadoFim && fim > ocupadoInicio;
    }

    async function carregarHorarios() {
        listaHorarios.innerHTML = '<p class="texto-carregando">Consultando horários...</p>';
        limparSelecaoHorario();

        const servico = servicoSelecionado();
        if (!servico) {
            listaHorarios.innerHTML = '<p>Escolha primeiro um serviço.</p>';
            return;
        }
        if (!dataSelecionada) {
            listaHorarios.innerHTML = '<p>Selecione uma data no calendário.</p>';
            return;
        }

        try {
            intervalosOcupados = await buscarIntervalosOcupados();
            listaHorarios.innerHTML = '';
            const agora = new Date();
            let quantidadeDisponivel = 0;

            horariosPossiveis.forEach(horario => {
                const inicio = new Date(`${dataIso(dataSelecionada)}T${horario}:00`);
                const fim = new Date(inicio.getTime() + Number(servico.duracao_minutos) * 60000);

                if (inicio <= agora || !horarioDentroDoExpediente(inicio, fim, horario)) return;
                const ocupado = intervalosOcupados.some(intervalo =>
                    conflitaComIntervalo(inicio, fim, intervalo)
                );
                if (ocupado) return;

                quantidadeDisponivel++;
                const botao = document.createElement('button');
                botao.type = 'button';
                botao.className = 'horario';
                botao.textContent = horario;
                botao.addEventListener('click', () => {
                    document.querySelectorAll('.horario.escolhido').forEach(item =>
                        item.classList.remove('escolhido')
                    );
                    botao.classList.add('escolhido');
                    horarioSelecionado = horario;
                    const preco = Number(servico.preco).toLocaleString('pt-BR', {
                        style: 'currency', currency: 'BRL'
                    });
                    resumoEscolha.textContent = `${servico.nome} — ${exibirData(dataSelecionada)} às ${horario} — ${preco}`;
                    confirmacaoBox.hidden = false;
                    mensagemAgendamento.textContent = '';
                });
                listaHorarios.appendChild(botao);
            });

            if (quantidadeDisponivel === 0) {
                listaHorarios.innerHTML = '<p>Não há horários disponíveis para este serviço e esta data. Escolha outro dia.</p>';
            }
        } catch (erro) {
            listaHorarios.innerHTML = `<p>${escaparHtml(erro.message)}</p>`;
        }
    }

    async function confirmarAgendamento() {
        const servico = servicoSelecionado();
        if (!servico || !dataSelecionada || !horarioSelecionado) {
            mensagemAgendamento.textContent = 'Selecione o serviço, a data e o horário.';
            return;
        }

        const botao = document.getElementById('confirmarAgendamento');
        botao.disabled = true;
        mensagemAgendamento.textContent = 'Salvando agendamento...';

        try {
            const token = await obterTokenCsrf();
            const resposta = await fetch('/api/agendamentos', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': token
                },
                body: JSON.stringify({
                    servicoId: Number(servico.id),
                    dataHora: `${dataIso(dataSelecionada)}T${horarioSelecionado}:00`
                })
            });

            if (!resposta.ok) {
                let erro = 'Não foi possível confirmar o agendamento.';
                try {
                    const dados = await resposta.json();
                    erro = dados.erro || dados.message || erro;
                } catch (_) { /* resposta sem JSON */ }
                if (resposta.status === 409) {
                    erro = 'Este horário acabou de ser reservado por outra cliente. Seu agendamento NÃO foi confirmado. Escolha outro horário.';
                }
                throw new Error(erro);
            }

            const agendamentoCriado = await resposta.json();
            if (resposta.status !== 201 || !agendamentoCriado || !agendamentoCriado.id) {
                throw new Error('O servidor não retornou a confirmação do agendamento. Não considere o horário reservado; atualize seus agendamentos antes de tentar novamente.');
            }

            // Confirmação final: só mostra sucesso se o registro também aparecer na consulta da própria cliente.
            const meusAgendamentos = await api('/api/agendamentos');
            const registroConfirmado = meusAgendamentos.some(item => Number(item.id) === Number(agendamentoCriado.id));
            if (!registroConfirmado) {
                throw new Error('A solicitação foi recebida, mas não conseguimos confirmar o registro na sua agenda. Atualize a página ou fale com a Mary antes de tentar novamente.');
            }

            mensagemAgendamento.textContent = 'Agendamento confirmado! Ele já aparece em Meus agendamentos.';
            limparSelecaoHorario();
            await carregarHorarios();
            await carregarAgendamentos();
        } catch (erro) {
            mensagemAgendamento.textContent = erro.message;
            // Atualiza tanto a disponibilidade quanto a lista pessoal após falha ou conflito.
            await carregarHorarios();
            await carregarAgendamentos();
        } finally {
            botao.disabled = false;
        }
    }

    function formatarDataHora(valor) {
        const data = dataHoraLocal(valor);
        return data.toLocaleString('pt-BR', {
            day: '2-digit', month: '2-digit', year: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    }

    function renderizarPainelAdminDia() {
        if (!dataSelecionada) {
            adminDataSelecionada.textContent = 'Selecione uma data';
            contagemConfirmados.textContent = '0';
            contagemCancelados.textContent = '0';
            adminAgendamentosDia.innerHTML = '<p class="admin-vazio">Selecione um dia no calendário para visualizar os atendimentos.</p>';
            painelAdminBloqueios.hidden = true;
            return;
        }

        const diaIso = dataIso(dataSelecionada);
        adminDataSelecionada.textContent = exibirData(dataSelecionada);
        const agendamentosDoDia = agendamentosCarregados
            .filter(item => String(item.data_hora || '').slice(0, 10) === diaIso)
            .sort((a, b) => String(a.data_hora).localeCompare(String(b.data_hora)));
        const confirmados = agendamentosDoDia.filter(item =>
            String(item.status || '').toLowerCase() === 'confirmado'
        );
        const cancelados = agendamentosDoDia.filter(item =>
            String(item.status || '').toLowerCase() === 'cancelado'
        );
        const concluidos = agendamentosDoDia.filter(item =>
            String(item.status || '').toLowerCase() === 'concluido'
        );

        contagemConfirmados.textContent = String(confirmados.length);
        contagemCancelados.textContent = String(cancelados.length);

        const itens = filtroAdmin === 'todos' ? agendamentosDoDia
            : filtroAdmin === 'cancelados' ? cancelados
            : filtroAdmin === 'concluidos' ? concluidos : confirmados;

        if (!itens.length) {
            const mensagens = {
                confirmados: 'Não há atendimentos confirmados nesta data.',
                cancelados: 'Não há atendimentos cancelados nesta data.',
                concluidos: 'Não há atendimentos concluídos nesta data.',
                todos: 'Não há agendamentos registrados nesta data.'
            };
            adminAgendamentosDia.innerHTML = `<p class="admin-vazio">${mensagens[filtroAdmin]}</p>`;
            return;
        }

        adminAgendamentosDia.innerHTML = itens.map(item => {
            const situacao = String(item.status || '').toLowerCase();
            const cancelado = situacao === 'cancelado';
            const concluido = situacao === 'concluido';
            const nomeServico = escaparHtml(item.servico_nome || 'Serviço');
            const nomeCliente = escaparHtml(item.cliente_nome || 'Cliente');
            const hora = escaparHtml(formatarHora(item.data_hora));
            const status = cancelado ? 'Cancelado' : concluido ? 'Concluído' : 'Confirmado';
            const classeStatus = cancelado ? 'admin-status-cancelado'
                : concluido ? 'admin-status-concluido' : 'admin-status-confirmado';
            const acao = situacao === 'confirmado' ?
                `<button type="button" class="botao-cancelar-admin" data-cancelar="${Number(item.id)}">Cancelar atendimento</button>` : '';

            return `
                <article class="admin-atendimento ${cancelado ? 'admin-atendimento-cancelado' : concluido ? 'admin-atendimento-concluido' : ''}">
                    <div class="admin-hora-atendimento">${hora}</div>
                    <div class="admin-detalhes-atendimento">
                        <strong>${nomeServico}</strong>
                        <span>${nomeCliente}</span>
                        <span class="admin-status ${classeStatus}">${status}</span>
                    </div>
                    ${acao}
                </article>
            `;
        }).join('');
    }

    function renderizarHistoricoAdmin(registros) {
        if (!Array.isArray(registros) || registros.length === 0) {
            listaHistoricoAdmin.innerHTML = '<p class="admin-vazio">Nenhum registro encontrado com esses filtros.</p>';
            return;
        }

        listaHistoricoAdmin.innerHTML = registros.map(item => {
            const situacao = String(item.status || '').toLowerCase();
            const cancelado = situacao === 'cancelado';
            const concluido = situacao === 'concluido';
            const statusTexto = cancelado ? 'Cancelado'
                : concluido ? 'Concluído' : 'Pendente de atualização';
            const statusClasse = cancelado ? 'admin-status-cancelado'
                : concluido ? 'admin-status-concluido' : 'admin-status-confirmado';
            const preco = Number(item.servico_preco || 0).toLocaleString('pt-BR', {
                style: 'currency', currency: 'BRL'
            });
            return `
                <article class="historico-admin-item ${cancelado ? 'historico-admin-cancelado' : ''}">
                    <div class="historico-admin-data">${escaparHtml(formatarDataHora(item.data_hora))}</div>
                    <div class="historico-admin-detalhes">
                        <strong>${escaparHtml(item.servico_nome || 'Serviço')}</strong>
                        <span>${escaparHtml(item.cliente_nome || 'Cliente')}</span>
                        <span>${escaparHtml(preco)} · ${Number(item.duracao_minutos || 0)} min</span>
                    </div>
                    <span class="admin-status ${statusClasse}">${statusTexto}</span>
                </article>
            `;
        }).join('');
    }

    async function carregarHistoricoAdmin(pagina = 0) {
        if (!usuarioAtual || usuarioAtual.perfil !== 'ADMIN' || historicoCarregando) return;
        const inicio = historicoInicioFiltro.value;
        const fim = historicoFimFiltro.value;
        if (Boolean(inicio) !== Boolean(fim)) {
            resumoHistoricoAdmin.textContent = 'Preencha as duas datas para filtrar por período.';
            return;
        }
        if (inicio && fim < inicio) {
            resumoHistoricoAdmin.textContent = 'A data final não pode ser anterior à data inicial.';
            return;
        }

        historicoCarregando = true;
        resumoHistoricoAdmin.textContent = 'Carregando histórico...';
        historicoAnteriorBtn.disabled = true;
        historicoProximoBtn.disabled = true;
        try {
            const parametros = new URLSearchParams({
                page: String(pagina),
                size: '20',
                status: historicoStatusFiltro.value
            });
            if (inicio && fim) {
                parametros.set('inicio', inicio);
                parametros.set('fim', fim);
            }
            const resultado = await api(`/api/agendamentos/historico?${parametros.toString()}`);
            historicoPaginaAtual = resultado.page;
            historicoTotalPaginas = resultado.totalPages;
            renderizarHistoricoAdmin(resultado.content);
            resumoHistoricoAdmin.textContent = `${Number(resultado.totalElements).toLocaleString('pt-BR')} registro(s) encontrado(s). Exibindo até 20 por página.`;
            historicoPaginaInfo.textContent = historicoTotalPaginas === 0
                ? 'Página 0 de 0'
                : `Página ${historicoPaginaAtual + 1} de ${historicoTotalPaginas}`;
            historicoAnteriorBtn.disabled = historicoPaginaAtual <= 0;
            historicoProximoBtn.disabled = historicoPaginaAtual + 1 >= historicoTotalPaginas;
        } catch (erro) {
            listaHistoricoAdmin.innerHTML = `<p class="admin-vazio">${escaparHtml(erro.message)}</p>`;
            resumoHistoricoAdmin.textContent = 'Não foi possível carregar o histórico.';
            historicoPaginaInfo.textContent = 'Página —';
        } finally {
            historicoCarregando = false;
        }
    }

    async function alternarHistoricoAdmin() {
        historicoAberto = !historicoAberto;
        painelHistoricoAdmin.hidden = !historicoAberto;
        const botao = document.getElementById('abrirHistoricoAdminBtn');
        botao.setAttribute('aria-expanded', String(historicoAberto));
        botao.innerHTML = historicoAberto
            ? '<i class="fa-solid fa-clock-rotate-left" aria-hidden="true"></i> Atualizar histórico'
            : '<i class="fa-solid fa-clock-rotate-left" aria-hidden="true"></i> Ver histórico completo';
        if (historicoAberto) await carregarHistoricoAdmin(0);
    }

    async function carregarAgendamentos() {
        try {
            let url = '/api/agendamentos';
            if (usuarioAtual.perfil === 'ADMIN') {
                const primeiroDia = new Date(mesExibido.getFullYear(), mesExibido.getMonth(), 1);
                const ultimoDia = new Date(mesExibido.getFullYear(), mesExibido.getMonth() + 1, 0);
                const parametros = new URLSearchParams({
                    inicio: dataIso(primeiroDia),
                    fim: dataIso(ultimoDia)
                });
                url += `?${parametros.toString()}`;
            } else if (mostrarEncerradosCliente) {
                url += '?incluirEncerrados=true';
            }
            agendamentosCarregados = await api(url);

            if (usuarioAtual.perfil === 'ADMIN') {
                renderizarCalendario();
                renderizarPainelAdminDia();
                return;
            }

            if (!agendamentosCarregados.length) {
                listaAgendamentos.innerHTML = mostrarEncerradosCliente
                    ? '<p>Você ainda não possui agendamentos registrados.</p>'
                    : '<p>Você não possui próximos atendimentos confirmados. Os cancelados e concluídos ficam ocultos nesta tela.</p>';
                return;
            }

            listaAgendamentos.innerHTML = agendamentosCarregados.map(agendamento => {
                const situacao = String(agendamento.status || '').toLowerCase();
                const cancelado = situacao === 'cancelado';
                const concluido = situacao === 'concluido';
                const dataAtendimento = String(agendamento.data_hora || '').slice(0, 10);
                const podeCancelar = situacao === 'confirmado' && dataAtendimento > dataIso(new Date());
                const statusTexto = cancelado ? 'Cancelado' : concluido ? 'Concluído' : 'Confirmado';
                const classeStatus = cancelado ? 'status-cancelado'
                    : concluido ? 'status-concluido' : 'status-ativo';
                const acaoCancelar = podeCancelar
                    ? `<button type="button" class="botao-cancelar" data-cancelar="${Number(agendamento.id)}">Cancelar agendamento</button>`
                    : (situacao === 'confirmado' && dataAtendimento === dataIso(new Date()))
                        ? '<span class="mensagem-cancelamento-indisponivel">Para cancelar no dia do atendimento, fale diretamente com a Mary.</span>'
                        : '';
                return `
                    <article class="card-agendamento ${cancelado ? 'card-agendamento-cancelado' : concluido ? 'card-agendamento-concluido' : ''}">
                        <div class="card-agendamento-info">
                            <strong>${escaparHtml(agendamento.servico_nome || 'Serviço')}</strong>
                            <span>${escaparHtml(formatarDataHora(agendamento.data_hora))}</span>
                            <span class="status-agendamento ${classeStatus}">${statusTexto}</span>
                        </div>
                        ${acaoCancelar}
                    </article>
                `;
            }).join('');
        } catch (erro) {
            if (usuarioAtual.perfil === 'ADMIN') {
                adminAgendamentosDia.innerHTML = `<p class="admin-vazio">${escaparHtml(erro.message)}</p>`;
            } else {
                listaAgendamentos.innerHTML = `<p>${escaparHtml(erro.message)}</p>`;
            }
        }
    }

    async function cancelarAgendamento(id) {
        const mensagem = usuarioAtual.perfil === 'ADMIN'
            ? 'Deseja cancelar este atendimento? O horário ficará livre novamente.'
            : 'O cancelamento pelo site só é permitido até o dia anterior ao atendimento. Deseja continuar?';
        if (!window.confirm(mensagem)) return;

        try {
            const token = await obterTokenCsrf();
            await api(`/api/agendamentos/${id}/cancelar`, {
                method: 'PATCH',
                headers: { 'X-XSRF-TOKEN': token }
            });
            await carregarAgendamentos();
            if (usuarioAtual.perfil !== 'ADMIN' && dataSelecionada) {
                await carregarHorarios();
            }
        } catch (erro) {
            window.alert(erro.message);
        }
    }

    document.getElementById('mesAnterior').addEventListener('click', async () => {
        mesExibido = new Date(mesExibido.getFullYear(), mesExibido.getMonth() - 1, 1);
        if (usuarioAtual.perfil === 'ADMIN') {
            dataSelecionada = null;
            painelAdminBloqueios.hidden = true;
            await carregarAgendamentos();
            await carregarBloqueiosAdmin();
        } else {
            await carregarDiasBloqueados();
            renderizarCalendario();
        }
    });

    document.getElementById('proximoMes').addEventListener('click', async () => {
        mesExibido = new Date(mesExibido.getFullYear(), mesExibido.getMonth() + 1, 1);
        if (usuarioAtual.perfil === 'ADMIN') {
            dataSelecionada = null;
            painelAdminBloqueios.hidden = true;
            await carregarAgendamentos();
            await carregarBloqueiosAdmin();
        } else {
            await carregarDiasBloqueados();
            renderizarCalendario();
        }
    });

    document.getElementById('bloquearIntervaloBtn').addEventListener('click', () => {
        const inicio = bloqueioInicio.value;
        const fim = bloqueioFim.value;
        if (!inicio || !fim) {
            mensagemBloqueio.textContent = 'Informe o horário inicial e final.';
            return;
        }
        salvarBloqueio(inicio, fim);
    });

    document.getElementById('bloquearDiaBtn').addEventListener('click', () => {
        if (!window.confirm(`Deseja bloquear todo o expediente de ${dataSelecionada ? exibirData(dataSelecionada) : 'esta data'} (08h às 17h)?`)) return;
        salvarBloqueio('08:00', '17:00');
    });

    listaBloqueiosDia.addEventListener('click', evento => {
        const botao = evento.target.closest('[data-excluir-bloqueio]');
        if (botao) excluirBloqueio(botao.dataset.excluirBloqueio);
    });

    servicoSelect.addEventListener('change', carregarHorarios);
    document.getElementById('confirmarAgendamento').addEventListener('click', confirmarAgendamento);
    document.getElementById('logoutBtn').addEventListener('click', async () => {
        try {
            const token = await obterTokenCsrf();
            await api('/api/auth/logout', {
                method: 'POST',
                headers: { 'X-XSRF-TOKEN': token }
            });
        } catch (_) {
            // Mesmo se a sessão já tiver expirado, retorna à tela de login.
        }
        window.location.href = '/index.html';
    });

    alternarEncerradosClienteBtn.addEventListener('click', async () => {
        mostrarEncerradosCliente = !mostrarEncerradosCliente;
        alternarEncerradosClienteBtn.textContent = mostrarEncerradosCliente
            ? 'Ocultar cancelados e concluídos'
            : 'Mostrar cancelados e concluídos';
        alternarEncerradosClienteBtn.setAttribute('aria-expanded', String(mostrarEncerradosCliente));
        descricaoAgendamentosCliente.textContent = mostrarEncerradosCliente
            ? 'Exibindo todos os seus registros, incluindo cancelados e concluídos.'
            : 'Aqui aparecem seus atendimentos confirmados de hoje e dos próximos dias.';
        await carregarAgendamentos();
    });

    listaAgendamentos.addEventListener('click', evento => {
        const botao = evento.target.closest('[data-cancelar]');
        if (botao) cancelarAgendamento(botao.dataset.cancelar);
    });

    adminAgendamentosDia.addEventListener('click', evento => {
        const botao = evento.target.closest('[data-cancelar]');
        if (botao) cancelarAgendamento(botao.dataset.cancelar);
    });

    document.querySelectorAll('[data-admin-filtro]').forEach(botao => {
        botao.addEventListener('click', () => {
            filtroAdmin = botao.dataset.adminFiltro;
            document.querySelectorAll('[data-admin-filtro]').forEach(item => {
                const ativo = item === botao;
                item.classList.toggle('ativo', ativo);
                item.setAttribute('aria-pressed', String(ativo));
            });
            renderizarPainelAdminDia();
        });
    });

    document.getElementById('abrirHistoricoAdminBtn').addEventListener('click', alternarHistoricoAdmin);
    document.getElementById('fecharHistoricoAdminBtn').addEventListener('click', () => {
        historicoAberto = false;
        painelHistoricoAdmin.hidden = true;
        const botao = document.getElementById('abrirHistoricoAdminBtn');
        botao.setAttribute('aria-expanded', 'false');
        botao.innerHTML = '<i class="fa-solid fa-clock-rotate-left" aria-hidden="true"></i> Ver histórico completo';
    });
    document.getElementById('filtrarHistoricoAdminBtn').addEventListener('click', () => carregarHistoricoAdmin(0));
    historicoAnteriorBtn.addEventListener('click', () => carregarHistoricoAdmin(historicoPaginaAtual - 1));
    historicoProximoBtn.addEventListener('click', () => carregarHistoricoAdmin(historicoPaginaAtual + 1));

    async function atualizarPainelAdmin() {
        if (!usuarioAtual || usuarioAtual.perfil !== 'ADMIN' || atualizacaoAdminEmAndamento) return;
        atualizacaoAdminEmAndamento = true;
        try {
            await carregarAgendamentos();
            await carregarBloqueiosAdmin();
            if (historicoAberto) await carregarHistoricoAdmin(historicoPaginaAtual);
        } catch (erro) {
            avisoAgenda.textContent = `Não foi possível atualizar a agenda: ${erro.message}`;
        } finally {
            atualizacaoAdminEmAndamento = false;
        }
    }

    document.getElementById('novoServicoBtn').addEventListener('click', () => abrirFormServico());
    servicoAdminForm.addEventListener('submit', salvarServicoAdmin);
    document.getElementById('cancelarEdicaoServicoBtn').addEventListener('click', fecharFormServico);
    listaServicosAdmin.addEventListener('click', evento => {
        const editar = evento.target.closest('[data-editar-servico]');
        const excluir = evento.target.closest('[data-excluir-servico]');
        if (editar) {
            const servico = servicos.find(item => Number(item.id) === Number(editar.dataset.editarServico));
            if (servico) abrirFormServico(servico);
        } else if (excluir) {
            excluirServicoAdmin(excluir.dataset.excluirServico);
        }
    });
    document.getElementById('atualizarAgendaAdminBtn').addEventListener('click', atualizarPainelAdmin);
    window.addEventListener('focus', () => {
        if (usuarioAtual?.perfil === 'ADMIN') {
            atualizarPainelAdmin();
        } else if (usuarioAtual) {
            verificarIdentidadeDaSessao()
                .then(() => carregarAgendamentos())
                .catch(() => {});
        }
    });

    async function iniciar() {
        try {
            await carregarUsuario();
            if (usuarioAtual.perfil === 'ADMIN') {
                await carregarBloqueiosAdmin();
                await carregarServicos();
                renderizarServicosAdmin();
                renderizarCalendario();
                listaBloqueiosDia.innerHTML = '<p>Selecione um dia no calendário para administrar os bloqueios.</p>';
                window.setInterval(() => {
                    if (!document.hidden) atualizarPainelAdmin();
                }, 20000);
            } else {
                await carregarServicos();
                await carregarDiasBloqueados();
                renderizarCalendario();
                listaHorarios.innerHTML = '<p>Escolha um serviço e uma data para consultar os horários.</p>';
            }
            await carregarAgendamentos();
        } catch (erro) {
            avisoAgenda.textContent = erro.message || 'Não foi possível carregar a agenda.';
        }
    }

    iniciar();
})();

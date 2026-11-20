async function obterTokenCsrf() {
    const resposta = await fetch('/api/auth/csrf', {
        method: 'GET',
        credentials: 'same-origin',
        cache: 'no-store'
    });

    if (!resposta.ok) {
        throw new Error('Não foi possível iniciar a conexão segura. Atualize a página e tente novamente.');
    }

    const dados = await resposta.json();
    return dados.token;
}

async function lerErro(resposta) {
    try {
        const dados = await resposta.json();
        return dados.erro || dados.message || dados.mensagem || 'Não foi possível concluir a operação.';
    } catch (_) {
        return 'Não foi possível concluir a operação. Verifique se o servidor está ligado.';
    }
}

const loginForm = document.getElementById('loginForm');
if (loginForm) {
    loginForm.addEventListener('submit', async (evento) => {
        evento.preventDefault();
        const mensagem = document.getElementById('loginMensagem');
        const botao = loginForm.querySelector('button[type="submit"]');
        mensagem.textContent = '';
        botao.disabled = true;
        botao.textContent = 'Entrando...';

        try {
            const token = await obterTokenCsrf();
            const resposta = await fetch('/api/auth/login', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': token
                },
                body: JSON.stringify({
                    email: document.getElementById('email').value.trim(),
                    senha: document.getElementById('senha').value
                })
            });

            if (!resposta.ok) {
                throw new Error(await lerErro(resposta));
            }

            window.location.href = '/agenda.html';
        } catch (erro) {
            mensagem.textContent = erro.message;
        } finally {
            botao.disabled = false;
            botao.textContent = 'Entrar';
        }
    });
}

const cadastroForm = document.getElementById('cadastroForm');
if (cadastroForm) {
    cadastroForm.addEventListener('submit', async (evento) => {
        evento.preventDefault();
        const mensagem = document.getElementById('cadastroMensagem');
        const botao = cadastroForm.querySelector('button[type="submit"]');
        const senha = document.getElementById('senha').value;
        const confirmarSenha = document.getElementById('confirmarSenha').value;
        mensagem.textContent = '';

        if (senha !== confirmarSenha) {
            mensagem.textContent = 'As senhas não conferem.';
            return;
        }

        botao.disabled = true;
        botao.textContent = 'Cadastrando...';

        try {
            const token = await obterTokenCsrf();
            const dadosCliente = {
                nome: document.getElementById('nome').value.trim(),
                email: document.getElementById('email').value.trim(),
                cpf: document.getElementById('cpf').value.trim(),
                senha: senha
            };

            const respostaCadastro = await fetch('/api/clientes', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': token
                },
                body: JSON.stringify(dadosCliente)
            });

            if (!respostaCadastro.ok) {
                throw new Error(await lerErro(respostaCadastro));
            }

            // Após o cadastro, autentica a nova conta automaticamente.
            const respostaLogin = await fetch('/api/auth/login', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/json',
                    'X-XSRF-TOKEN': token
                },
                body: JSON.stringify({ email: dadosCliente.email, senha: senha })
            });

            if (!respostaLogin.ok) {
                mensagem.textContent = 'Cadastro realizado! Agora faça login com seu e-mail e senha.';
                window.setTimeout(() => window.location.href = '/index.html', 1800);
                return;
            }

            window.location.href = '/agenda.html';
        } catch (erro) {
            mensagem.textContent = erro.message;
        } finally {
            botao.disabled = false;
            botao.textContent = 'Cadastrar';
        }
    });
}

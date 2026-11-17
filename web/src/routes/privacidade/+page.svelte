<svelte:head>
	<title>Política de privacidade — Bix Player Pro</title>
	<meta name="description" content="Como o app Bix Player Pro trata os dados do seu aparelho." />
</svelte:head>

<main class="mx-auto max-w-3xl px-4 py-10 text-slate-800 dark:text-slate-100">
	<h1 class="text-2xl font-semibold">Política de privacidade — Bix Player Pro</h1>
	<p class="mt-2 text-sm text-slate-500">Em vigor desde 17 de novembro de 2026.</p>

	<div class="policy mt-8 space-y-6 leading-relaxed">
		<section>
			<h2>Quem somos</h2>
			<p>
				O Bix Player Pro é um player de vídeo para celular e Android TV, mantido por
				<strong>Jose Lizandro Santos Diniz</strong>, responsável pelo tratamento dos dados descrito
				aqui. Contato: <a href="mailto:contato@bixplayer.pro">contato@bixplayer.pro</a>.
			</p>
			<p>
				O app não fornece nem hospeda conteúdo. Ele reproduz as listas configuradas para cada
				aparelho pelo revendedor que o ativou.
			</p>
		</section>

		<section>
			<h2>Dados que tratamos</h2>
			<ul>
				<li>
					<strong>Identificador do aparelho.</strong> O identificador do Android (ANDROID_ID) é guardado
					só em forma de hash. A partir dele o app gera o código MAC exibido na tela, usado para a ativação.
				</li>
				<li>
					<strong>Dados de uso do app:</strong> versão do app, tipo de aparelho (TV ou celular) e data
					do último acesso.
				</li>
				<li>
					<strong>Endereço IP</strong>, em registros de segurança e no controle de excesso de
					requisições.
				</li>
				<li>
					<strong>Listas de reprodução:</strong> endereço da lista e, quando houver, o usuário e a senha
					do provedor. A senha é guardada criptografada.
				</li>
				<li>
					<strong>Dados informados pelo revendedor:</strong> nome do cliente e validade da licença.
				</li>
				<li>
					<strong>Diagnóstico</strong>, apenas quando você toca em "Enviar diagnóstico": fabricante
					e modelo do aparelho, versão do Android, memória, decodificadores de vídeo e os registros
					recentes do app. Guardamos os 10 envios mais recentes de cada aparelho.
				</li>
			</ul>
			<p>
				Favoritos, progresso de reprodução e preferências ficam só no aparelho. Não coletamos nome,
				e-mail, telefone, localização, contatos nem o identificador de publicidade. O app não exibe
				anúncios e não vende dados.
			</p>
		</section>

		<section>
			<h2>Para que usamos</h2>
			<p>
				Para ativar o aparelho, entregar a configuração e as listas, verificar a validade da
				licença, proteger o serviço contra abuso e dar suporte quando você envia um diagnóstico.
			</p>
		</section>

		<section>
			<h2>Com quem compartilhamos</h2>
			<ul>
				<li>
					O <strong>revendedor</strong> que ativou o aparelho vê o MAC, o nome do cliente, a validade,
					as listas e os diagnósticos enviados.
				</li>
				<li>
					O <strong>provedor da lista</strong> recebe as requisições de vídeo diretamente do seu aparelho,
					sob as regras dele.
				</li>
				<li>
					A <strong>empresa de hospedagem</strong> do nosso servidor armazena os dados em nosso nome.
				</li>
			</ul>
			<p>Não compartilhamos dados para publicidade.</p>
		</section>

		<section>
			<h2>Por quanto tempo guardamos e como pedir a exclusão</h2>
			<p>
				Os dados do aparelho ficam guardados enquanto ele estiver cadastrado. Quando o revendedor
				exclui o aparelho, apagamos os dados dele e as listas. Registros de segurança são mantidos
				pelo tempo necessário à segurança do serviço e ao cumprimento de obrigações legais.
			</p>
			<p>
				Para pedir acesso, correção ou exclusão dos seus dados, escreva para
				<a href="mailto:contato@bixplayer.pro">contato@bixplayer.pro</a> informando o código MAC exibido
				no app. Respondemos em até 15 dias, conforme a LGPD (Lei 13.709/2018).
			</p>
		</section>

		<section>
			<h2>Segurança</h2>
			<p>
				A comunicação entre o app e o servidor usa conexão criptografada (HTTPS). Senhas de listas
				ficam criptografadas e o identificador do aparelho fica em hash.
			</p>
		</section>

		<section>
			<h2>Crianças</h2>
			<p>O app não é direcionado a menores de 13 anos.</p>
		</section>

		<section>
			<h2>Mudanças</h2>
			<p>
				Quando esta política mudar, a nova versão será publicada nesta página com a data de vigência
				atualizada.
			</p>
		</section>
	</div>
</main>

<style>
	.policy h2 {
		font-size: 1.125rem;
		font-weight: 600;
		margin-bottom: 0.5rem;
	}
	.policy ul {
		list-style: disc;
		padding-left: 1.25rem;
	}
	.policy li + li {
		margin-top: 0.5rem;
	}
	.policy p + p {
		margin-top: 0.75rem;
	}
	.policy a {
		color: #2563eb;
		text-decoration: underline;
	}
</style>

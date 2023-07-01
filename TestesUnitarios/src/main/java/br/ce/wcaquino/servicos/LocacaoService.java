package br.ce.wcaquino.servicos;

import br.ce.wcaquino.daos.LocacaoDAO;
import br.ce.wcaquino.entidades.Filme;
import br.ce.wcaquino.entidades.Locacao;
import br.ce.wcaquino.entidades.Usuario;
import br.ce.wcaquino.exceptions.FilmeSemEstoqueException;
import br.ce.wcaquino.exceptions.LocadoraException;
import br.ce.wcaquino.utils.DataUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static br.ce.wcaquino.utils.DataUtils.adicionarDias;

public class LocacaoService
{
	private LocacaoDAO dao;
	private SPCService spcService;
	private EmailService emailService;

	public Locacao alugarFilme(Usuario usuario, List<Filme> filmes) throws FilmeSemEstoqueException, LocadoraException
	{
		if (usuario == null)
		{
			throw new LocadoraException("Usuário vazio");
		}

		if (filmes == null || filmes.isEmpty())
		{
			throw new LocadoraException("Lista de Filmes vazia");
		}

		List<Filme> filmesLocar = new ArrayList<>();
		filmes.forEach(f -> {if (f.getEstoque() != 0) {filmesLocar.add(f);}});

		//if(filmesLocar.size() < filmes.size()) {throw new FilmeSemEstoqueException();}
		if (filmesLocar.size() == 0) {throw new FilmeSemEstoqueException();}

		Locacao locacao = getLocacao(usuario, filmesLocar);

		boolean negativado;
		try
		{
			negativado = spcService.possuiNegativacao(usuario);
		}
		catch (Exception e)
		{
			throw new LocadoraException("Problemas com SPC, tente novamente");
		}

		if (negativado)
		{
			throw new LocadoraException("Usuário Negativado");
		}

		//Salvando a locacao...
		dao.salvar(locacao);

		return locacao;
	}

	public void notificarAtraso()
	{
		List<Locacao> locacoes = dao.obterLocacoesPendentes();
		locacoes.forEach(l -> {if (l.getDataRetorno().before(obterData())) {emailService.notificarAtraso(l.getUsuario());}});
	}

	public void prorrogarLocacao(Locacao locacao, int dias)
	{
		Locacao novaLocacao = new Locacao();

		novaLocacao.setUsuario(locacao.getUsuario());
		novaLocacao.setFilmes(locacao.getFilmes());
		novaLocacao.setDataLocacao(locacao.getDataLocacao());
		novaLocacao.setDataRetorno(DataUtils.obterDataComDiferencaDias(dias));
		novaLocacao.setValor(locacao.getValor()*dias);

		dao.salvar(novaLocacao);
	}

	private Locacao getLocacao(Usuario usuario, List<Filme> filmes)
	{
		Locacao locacao = new Locacao();
		locacao.setFilmes(filmes);
		locacao.setUsuario(usuario);
		locacao.setDataLocacao(obterData());
		locacao.setValor(calcularValorLocacao(filmes));

		//Entrega no dia seguinte
		locacao = getDataEntrega(locacao);
		return locacao;
	}

	protected Date obterData()
	{
		return new Date();
	}

	private Double calcularValorLocacao(List<Filme> filmes)
	{
		System.out.println("Estou calculando o valor da locação");

		Double[] valorLocacao = new Double[1];
		valorLocacao[0] = 0.0;

		int[] contador = new int[1];
		contador[0] = 0;

		filmes.forEach(f ->
		{
			contador[0]++;
			Double valorFilme = f.getPrecoLocacao();

			switch (contador[0])
			{
			case 3:
				valorFilme = valorFilme * 0.75;
				break;
			case 4:
				valorFilme = valorFilme * 0.5;
				break;
			case 5:
				valorFilme = valorFilme * 0.25;
				break;
			case 6:
				valorFilme = 0d;
				break;
			}

			valorLocacao[0] += valorFilme;
		});
		return valorLocacao[0];
	}

	private Locacao getDataEntrega(Locacao locacao)
	{
		Date dataEntrega = obterData();
		dataEntrega = adicionarDias(dataEntrega, 1);

		if (DataUtils.verificarDiaSemana(dataEntrega, Calendar.SUNDAY))
		{
			dataEntrega = adicionarDias(dataEntrega, 1);
		}
		locacao.setDataRetorno(dataEntrega);

		return locacao;
	}
}
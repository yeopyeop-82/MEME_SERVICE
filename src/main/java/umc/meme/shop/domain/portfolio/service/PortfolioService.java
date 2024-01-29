package umc.meme.shop.domain.portfolio.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import umc.meme.shop.domain.artist.entity.Artist;
import umc.meme.shop.domain.artist.repository.ArtistRepository;
import umc.meme.shop.domain.favorite.entity.FavoritePortfolio;
import umc.meme.shop.domain.portfolio.converter.PortfolioConverter;
import umc.meme.shop.domain.portfolio.dto.request.CreatePortfolioDto;
import umc.meme.shop.domain.portfolio.dto.request.UpdatePortfolioDto;
import umc.meme.shop.domain.portfolio.dto.response.PortfolioDto;
import umc.meme.shop.domain.portfolio.dto.response.PortfolioImgDto;
import umc.meme.shop.domain.portfolio.dto.response.PortfolioPageDto;
import umc.meme.shop.domain.portfolio.entity.Portfolio;
import umc.meme.shop.domain.portfolio.entity.PortfolioImg;
import umc.meme.shop.domain.portfolio.repository.PortfolioImgRepository;
import umc.meme.shop.domain.portfolio.repository.PortfolioRepository;
import umc.meme.shop.global.ErrorStatus;
import umc.meme.shop.global.exception.GlobalException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final ArtistRepository artistRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioImgRepository portfolioImgRepository;

    //포트폴리오 생성
    @Transactional
    public void createPortfolio(CreatePortfolioDto portfolioDto) {
        Artist artist = artistRepository.findById(portfolioDto.getArtistId())
                .orElseThrow(() -> new GlobalException(ErrorStatus.NOT_EXIST_ARTIST));

        List<PortfolioImg> portfolioImgList = new ArrayList<>();
        for (String src : portfolioDto.getPortfolioImgSrc()) {
            PortfolioImg portfolioImg = new PortfolioImg();
            portfolioImg.setSrc(src);
            portfolioImgList.add(portfolioImg);
        }


        //포트폴리오 이름이 이미 존재할 시
        if(portfolioRepository.existsByMakeupName(portfolioDto.getMakeupName()))
            throw new GlobalException(ErrorStatus.ALREADY_EXIST_PORTFOLIO);

        Portfolio portfolio = Portfolio.builder()
                .artist(artist)
                .category(portfolioDto.getCategory())
                .makeupName(portfolioDto.getMakeupName())
                .info(portfolioDto.getInfo())
                .price(portfolioDto.getPrice())
                .portfolioImgList(new ArrayList<PortfolioImg>())
                .averageStars("0.00")
                .isBlock(false)
                .build();

        for (PortfolioImg portfolioImg : portfolioImgList) {
            portfolioImg.setPortfolio(portfolio); // Portfolio 객체 설정
            portfolio.getPortfolioImgList().add(portfolioImg); // Portfolio의 이미지 리스트에 추가
        }

        System.out.println(portfolio.getPortfolioImgList());

        artist.updatePortfolioList(portfolio);
        portfolioRepository.save(portfolio);
    }

    // 포트폴리오 전체 조회
    @Transactional
    public PortfolioPageDto getPortfolio(Long artistId, int page) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new GlobalException(ErrorStatus.NOT_EXIST_ARTIST));

        //page
        List<Portfolio> portfolioList = artist.getPortfolioList();
        Pageable pageable = PageRequest.of(page, 30);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), portfolioList.size());

        //list를 page로 변환
        Page<Portfolio> portfolioPage = new PageImpl<>(portfolioList.subList(start, end),
                pageable, portfolioList.size());

        return PortfolioConverter.portfolioPageConverter(portfolioPage);
    }

    // 포트폴리오 하나만 조회
    public PortfolioDto getPortfolioDetails(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new GlobalException(ErrorStatus.NOT_EXIST_PORTFOLIO));

        return PortfolioDto.from(portfolio);
    }


    // 포트폴리오 수정/삭제
    @Transactional
    public void updatePortfolio(UpdatePortfolioDto request) {
        Artist artist = artistRepository.findById(request.getArtistId())
                .orElseThrow(() -> new GlobalException(ErrorStatus.NOT_EXIST_ARTIST));

        // Portfolio를 getPortfolioDetails 메서드를 이용해 조회
        PortfolioDto portfolioDto = getPortfolioDetails(request.getPortfolioId());
        Portfolio portfolio = portfolioRepository.findById(portfolioDto.getPortfolioId())
                    .orElseThrow(() -> new GlobalException(ErrorStatus.NOT_EXIST_PORTFOLIO));

        // 아티스트가 해당 portfolio에 권한이 없을때 (포트폴리오에 있는 artist가 본인이 아닐때)
        if (!portfolio.getArtist().equals(artist)) {
            throw new GlobalException(ErrorStatus.NOT_AUTHORIZED_PORTFOLIO);
        }

        if (request.getPortfolioImg() != null) {
            PortfolioImgDto portfolioImgDto = request.getPortfolioImg();
            PortfolioImg portfolioImg = portfolioImgRepository.findById(portfolioImgDto.getPortfolioImgId())
                    .orElseThrow(() -> new RuntimeException("포트폴리오 이미지를 찾을 수 없습니다."));

            if (portfolioImgDto.isDelete()) {
                // 이미지 삭제
                portfolio.getPortfolioImgList().remove(portfolioImg);
                portfolioImgRepository.delete(portfolioImg);
            } else if (portfolioImgDto.getPortfolioImgSrc() != null) {
                // 이미지 수정 (src 업데이트)
                portfolioImg.setSrc(portfolioImgDto.getPortfolioImgSrc());
                portfolioImgRepository.save(portfolioImg);

                // 업데이트된 이미지 정보를 포트폴리오의 이미지 리스트에 반영
                portfolio.getPortfolioImgList().removeIf(img -> img.getPortfolioImgId().equals(portfolioImg.getPortfolioImgId()));
                portfolio.getPortfolioImgList().add(portfolioImg);
            }
        }
        // Portfolio 업데이트
        portfolio.updatePortfolio(request);
    }


}

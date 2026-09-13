package duocuc.cl.rodrigo.carniverocrud.controller.config;

import duocuc.cl.rodrigo.carniverocrud.models.entity.Planta;
import duocuc.cl.rodrigo.carniverocrud.repository.PlantaJpaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@Configuration
public class PlantaSeedConfig {
    private static final Logger log = LoggerFactory.getLogger(PlantaSeedConfig.class);

    @Bean 
    public CommandLineRunner seedPlantas(PlantaJpaRepository plantaJpaRepository) {
        return args -> {
            // Idempotente: si ya hay plantas, no vuelve a insertar nada.
            if (plantaJpaRepository.count() > 0) {
                log.info("Seed de plantas omitido: ya existen {} registros.", plantaJpaRepository.count());
                return;
            }

            List<Planta> plantasIniciales = List.of(
                crearPlanta("Dionaea muscipula", 8990, "/images/Genlisea.webp",
                        "10-15 cm", "Interior/Exterior, alta humedad",
                        "Conocida como Venus atrapamoscas, cierra sus hojas al detectar presas.", 25),

                crearPlanta("Drosera capensis", 6990, "/images/Drosera.jpg",
                        "15-20 cm", "Interior, luz indirecta intensa",
                        "Planta pegajosa que atrapa insectos con sus tentáculos glandulares.", 30),

                crearPlanta("Nepenthes alata", 14990, "/images/Nepenthes.webp",
                        "30-50 cm (colgante)", "Semisombra, alta humedad ambiental",
                        "Produce jarras colgantes que atrapan y digieren insectos.", 15),

                crearPlanta("Sarracenia purpurea", 12990, "/images/Sarracenias.jpg",
                        "20-30 cm", "Exterior, pleno sol, sustrato húmedo",
                        "Planta jarra de origen norteamericano, resistente al frío.", 18),

                crearPlanta("Pinguicula moranensis", 7990, "/images/pinguicula.jpg",
                        "8-12 cm", "Interior, luz indirecta",
                        "Sus hojas pegajosas atrapan pequeños insectos como moscas de la fruta.", 22),

                crearPlanta("Utricularia gibba", 5990, "/images/Aldrovanda.jpeg",
                        "5-10 cm (acuática)", "Acuario o suelo húmedo, sol parcial",
                        "Planta carnívora acuática que atrapa microorganismos con vejigas.", 20)
            );

            plantaJpaRepository.saveAll(plantasIniciales);
            log.info("Seed completado: {} plantas cargadas.", plantasIniciales.size());
        };
    }

    private Planta crearPlanta(String name, int price, String image, String size,String planting, String description, int stock) {
        Planta planta = new Planta();
        planta.setName(name);
        planta.setPrice(price);
        planta.setImage(image);
        planta.setSize(size);
        planta.setPlanting(planting);
        planta.setDescription(description);
        planta.setStock(stock);
        return planta;
    }
}


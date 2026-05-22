package com.mathwise.backend.config;

import com.mathwise.backend.entity.Exercise;
import com.mathwise.backend.entity.KnowledgeNode;
import com.mathwise.backend.repository.ExerciseRepository;
import com.mathwise.backend.repository.KnowledgeNodeRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements ApplicationRunner {

    private final KnowledgeNodeRepository knowledgeNodeRepository;
    private final ExerciseRepository exerciseRepository;

    public DataSeeder(KnowledgeNodeRepository knowledgeNodeRepository, ExerciseRepository exerciseRepository) {
        this.knowledgeNodeRepository = knowledgeNodeRepository;
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (knowledgeNodeRepository.count() > 0) return;

        KnowledgeNode addition = node("ARITH_ADDITION", "Addition", 1, null);
        KnowledgeNode subtraction = node("ARITH_SUBTRACTION", "Subtraction", 1, addition);
        KnowledgeNode multiplication = node("ARITH_MULTIPLICATION", "Multiplication", 2, addition);
        KnowledgeNode division = node("ARITH_DIVISION", "Division", 2, multiplication);
        KnowledgeNode fractionsSimplify = node("FRACTIONS_SIMPLIFY", "Simplifying Fractions", 3, division);
        KnowledgeNode fractionsAddSub = node("FRACTIONS_ADD_SUB", "Adding & Subtracting Fractions", 3, fractionsSimplify);
        KnowledgeNode algebraLinear = node("ALGEBRA_LINEAR", "Linear Equations", 4, subtraction);
        KnowledgeNode algebraFactorize = node("ALGEBRA_FACTORIZE", "Factorization", 5, algebraLinear);

        List<KnowledgeNode> nodes = List.of(
                addition, subtraction, multiplication, division,
                fractionsSimplify, fractionsAddSub, algebraLinear, algebraFactorize
        );
        knowledgeNodeRepository.saveAll(nodes);

        seedExercises(addition, subtraction, multiplication, division,
                fractionsSimplify, fractionsAddSub, algebraLinear, algebraFactorize);
    }

    private KnowledgeNode node(String code, String title, int difficulty, KnowledgeNode prereq) {
        KnowledgeNode n = new KnowledgeNode();
        n.setNodeCode(code);
        n.setTitle(title);
        n.setDifficultyLevel(difficulty);
        n.setActive(true);
        n.setPrerequisiteNode(prereq);
        return n;
    }

    private void seedExercises(KnowledgeNode addition, KnowledgeNode subtraction,
                                KnowledgeNode multiplication, KnowledgeNode division,
                                KnowledgeNode fractionsSimplify, KnowledgeNode fractionsAddSub,
                                KnowledgeNode algebraLinear, KnowledgeNode algebraFactorize) {

        List<Exercise> exercises = List.of(
            // Addition
            ex(addition, "What is 47 + 38?", "85", 1),
            ex(addition, "Calculate 123 + 456.", "579", 1),
            ex(addition, "What is 999 + 1?", "1000", 1),
            ex(addition, "Find the sum of 256 and 744.", "1000", 2),

            // Subtraction
            ex(subtraction, "What is 100 - 37?", "63", 1),
            ex(subtraction, "Calculate 500 - 289.", "211", 2),
            ex(subtraction, "What is 1000 - 456?", "544", 2),
            ex(subtraction, "Find the difference between 800 and 347.", "453", 2),

            // Multiplication
            ex(multiplication, "What is 7 × 8?", "56", 1),
            ex(multiplication, "Calculate 12 × 15.", "180", 2),
            ex(multiplication, "What is 24 × 5?", "120", 2),
            ex(multiplication, "Find the product of 13 and 11.", "143", 2),

            // Division
            ex(division, "What is 84 ÷ 7?", "12", 1),
            ex(division, "Calculate 144 ÷ 12.", "12", 2),
            ex(division, "What is 225 ÷ 15?", "15", 2),
            ex(division, "Divide 256 by 8.", "32", 2),

            // Fractions - Simplify
            ex(fractionsSimplify, "Simplify 8/12.", "2/3", 3),
            ex(fractionsSimplify, "Simplify 15/25.", "3/5", 3),
            ex(fractionsSimplify, "Simplify 18/24.", "3/4", 3),
            ex(fractionsSimplify, "Simplify 36/48.", "3/4", 3),

            // Fractions - Add/Sub
            ex(fractionsAddSub, "Calculate 1/4 + 1/4.", "1/2", 3),
            ex(fractionsAddSub, "What is 3/5 + 1/5?", "4/5", 3),
            ex(fractionsAddSub, "Calculate 2/3 - 1/6.", "1/2", 4),
            ex(fractionsAddSub, "What is 5/6 - 1/3?", "1/2", 4),

            // Algebra - Linear Equations
            ex(algebraLinear, "Solve for x: x + 5 = 12.", "7", 4),
            ex(algebraLinear, "Solve for x: 2x = 18.", "9", 4),
            ex(algebraLinear, "Solve for x: 3x - 4 = 11.", "5", 4),
            ex(algebraLinear, "Solve for x: x/4 + 2 = 6.", "16", 5),

            // Algebra - Factorization
            ex(algebraFactorize, "Factorize: x^2 - 9.", "(x-3)(x+3)", 5),
            ex(algebraFactorize, "Factorize: x^2 + 5x + 6.", "(x+2)(x+3)", 5),
            ex(algebraFactorize, "Factorize: x^2 - 7x + 12.", "(x-3)(x-4)", 5),
            ex(algebraFactorize, "Factorize: 2x^2 + 6x.", "2x(x+3)", 5)
        );

        exerciseRepository.saveAll(exercises);
    }

    private Exercise ex(KnowledgeNode node, String question, String answer, int difficulty) {
        Exercise e = new Exercise();
        e.setKnowledgeNode(node);
        e.setQuestionText(question);
        e.setCorrectAnswer(answer);
        e.setDifficultyLevel(difficulty);
        e.setActive(true);
        return e;
    }
}

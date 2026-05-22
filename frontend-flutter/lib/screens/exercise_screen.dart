import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../models/exercise.dart';
import '../models/ai_feedback.dart';
import '../services/exercise_service.dart';

class ExerciseScreen extends StatefulWidget {
  const ExerciseScreen({super.key});

  @override
  State<ExerciseScreen> createState() => _ExerciseScreenState();
}

class _ExerciseScreenState extends State<ExerciseScreen> {
  Exercise? _exercise;
  bool _loading = true;
  bool _submitting = false;
  String? _error;
  final _answerController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadExercise();
  }

  @override
  void dispose() {
    _answerController.dispose();
    super.dispose();
  }

  Future<void> _loadExercise() async {
    setState(() { _loading = true; _error = null; });
    try {
      final exercise = await ExerciseService.getNextExercise();
      if (mounted) setState(() { _exercise = exercise; _loading = false; });
    } catch (e) {
      if (mounted) setState(() { _error = e.toString(); _loading = false; });
    }
  }

  Future<void> _submit() async {
    final answer = _answerController.text.trim();
    if (answer.isEmpty || _exercise == null) return;

    setState(() { _submitting = true; _error = null; });
    try {
      final AiFeedback feedback = await ExerciseService.submitAnswer(
        nodeCode: _exercise!.nodeCode,
        equation: _exercise!.questionText,
        correctAnswer: _exercise!.correctAnswer,
        studentAnswer: answer,
      );
      if (mounted) {
        _answerController.clear();
        context.push('/feedback', extra: feedback);
      }
    } catch (e) {
      if (mounted) setState(() { _error = e.toString(); _submitting = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_exercise != null ? _exercise!.nodeTitle : 'Practice'),
        leading: IconButton(
          icon: const Icon(Icons.home),
          onPressed: () => context.go('/home'),
        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(_error!, style: const TextStyle(color: Colors.red)),
                      const SizedBox(height: 16),
                      ElevatedButton(onPressed: _loadExercise, child: const Text('Retry')),
                    ],
                  ),
                )
              : Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _DifficultyChip(level: _exercise!.difficultyLevel),
                      const SizedBox(height: 24),
                      Text(
                        _exercise!.questionText,
                        style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 32),
                      TextField(
                        controller: _answerController,
                        decoration: const InputDecoration(
                          labelText: 'Your answer',
                          border: OutlineInputBorder(),
                          hintText: 'Type your answer here...',
                        ),
                        onSubmitted: (_) => _submit(),
                        textInputAction: TextInputAction.done,
                      ),
                      const SizedBox(height: 24),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: _submitting ? null : _submit,
                          child: _submitting
                              ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                              : const Text('Submit Answer'),
                        ),
                      ),
                    ],
                  ),
                ),
    );
  }
}

class _DifficultyChip extends StatelessWidget {
  final int level;
  const _DifficultyChip({required this.level});

  @override
  Widget build(BuildContext context) {
    final labels = {1: 'Easy', 2: 'Easy', 3: 'Medium', 4: 'Hard', 5: 'Hard'};
    final colors = {1: Colors.green, 2: Colors.green, 3: Colors.orange, 4: Colors.red, 5: Colors.red};
    return Chip(
      label: Text(labels[level] ?? 'Level $level', style: const TextStyle(color: Colors.white, fontSize: 12)),
      backgroundColor: colors[level] ?? Colors.grey,
      padding: EdgeInsets.zero,
    );
  }
}

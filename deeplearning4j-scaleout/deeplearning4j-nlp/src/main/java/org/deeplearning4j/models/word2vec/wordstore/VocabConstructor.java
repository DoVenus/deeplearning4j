package org.deeplearning4j.models.word2vec.wordstore;

import lombok.Data;
import lombok.NonNull;
import org.deeplearning4j.models.abstractvectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.abstractvectors.sequence.Sequence;
import org.deeplearning4j.models.abstractvectors.sequence.SequenceElement;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.Huffman;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.InMemoryLookupCache;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.interoperability.SentenceIteratorConverter;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * This class can be used to build joint vocabulary from special sources, that should be treated separately.
 * I.e. words from one source should have minWordFrequency set to 1, while the rest of corpus should have minWordFrequency set to 5.
 * So, here's the way to deal with it.
 *
 * It also can be used to simply build vocabulary out of arbitrary number of Sequences derived from arbitrary number of SequenceIterators
 *
 * @author raver119@gmail.com
 */
public class VocabConstructor<T extends SequenceElement> {
    private List<VocabSource<T>> sources = new ArrayList<>();
    private VocabCache<T> cache;
    private List<String> stopWords;
    private boolean useAdaGrad = false;
    private boolean fetchLabels = false;

    protected static final Logger log = LoggerFactory.getLogger(VocabConstructor.class);

    private VocabConstructor() {

    }

    /**
     * Placeholder for future implementation
     * @return
     */
    protected WeightLookupTable buildExtendedLookupTable() {
        return null;
    }

    /**
     * Placeholder for future implementation
     * @return
     */
    protected VocabCache buildExtendedVocabulary() {
        return null;
    }

    /**
     * This method scans all sources passed through builder, and returns all words as vocab.
     * If TargetVocabCache was set during instance creation, it'll be filled too.
     *
     *
     * @return
     */
    public VocabCache buildJointVocabulary(boolean resetCounters, boolean buildHuffmanTree) {
        if (resetCounters && buildHuffmanTree) throw new IllegalStateException("You can't reset counters and build Huffman tree at the same time!");

        if (cache == null) cache = new AbstractCache.Builder<T>().build();

        /*
        VocabularyHolder topHolder = new VocabularyHolder.Builder()
                .externalCache(cache)
                .minWordFrequency(0)
                .build();
        */

        AbstractCache<T> topHolder = new AbstractCache.Builder<T>()
                .minElementFrequency(0)
                .build();

        int cnt = 0;
        for(VocabSource<T> source: sources) {
            SequenceIterator<T> iterator = source.getIterator();
            iterator.reset();

            log.info("Trying source iterator: ["+ cnt+"]");
            cnt++;

            /*
            VocabularyHolder tempHolder = new VocabularyHolder.Builder()
                    .minWordFrequency(source.getMinWordFrequency())
                    .build();
                    */
            AbstractCache<T> tempHolder = new AbstractCache.Builder<T>().build();

            while (iterator.hasMoreSequences()) {
                Sequence<T> document = iterator.nextSequence();
              //  log.info("Sequence length: ["+ document.getElements().size()+"]");
             //   Tokenizer tokenizer = tokenizerFactory.create(document.getContent());




                if (fetchLabels) {
/*                    VocabularyWord word = new VocabularyWord(document.getSequenceLabel().getLabel());
                    word.setSpecial(true);
                    word.setCount(1);
                    */

                    T labelWord = createInstance();
                    labelWord.setSpecial(true);
                    labelWord.setElementFrequency(1);

                    // tempHolder.addWord(word);
                    tempHolder.addToken(labelWord);
//                    log.info("LabelledDocument: " + document);
                }

                List<String> tokens = document.asLabels();
                for (String token: tokens) {
                    if (stopWords !=null && stopWords.contains(token)) continue;
                    if (token == null || token.isEmpty()) continue;

                    if (!tempHolder.containsWord(token)) {
                        tempHolder.addToken(document.getElementByLabel(token));

                        // TODO: this line should be uncommented only after AdaGrad is fixed, so the size of AdaGrad array is known
                        /*
                        if (useAdaGrad) {
                            VocabularyWord word = tempHolder.getVocabularyWordByString(token);

                            word.setHistoricalGradient(new double[layerSize]);
                        }
                        */
                    } else {
                        tempHolder.incrementWordCount(token);
                    }
                }
            }
            // apply minWordFrequency set for this source
            log.info("Vocab size before truncation: " + tempHolder.numWords());
            //tempHolder.truncateVocabulary();

            log.info("Vocab size after truncation: " + tempHolder.numWords());

            // at this moment we're ready to transfer
            topHolder.importVocabulary(tempHolder);
        }

        // at this moment, we have vocabulary full of words, and we have to reset counters before transfer everything back to VocabCache
        if (resetCounters) {
            for (T element: topHolder.vocabWords()) {
                element.setElementFrequency(0);
            }
            topHolder.updateWordsOccurencies();;
        }
            //topHolder.resetWordCounters();

        if (buildHuffmanTree) {
            Huffman huffman = new Huffman(topHolder.vocabWords());
            huffman.build();
            huffman.applyIndexes(topHolder);
            //topHolder.updateHuffmanCodes();
        }

        //topHolder.transferBackToVocabCache(cache);
        cache.importVocabulary(topHolder);
        return cache;
    }

    protected T createInstance() {
        try {
            return (T) ((Class) ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0]).newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class Builder<T extends SequenceElement> {
        private List<VocabSource<T>> sources = new ArrayList<>();
        private VocabCache<T> cache;
        private List<String> stopWords = new ArrayList<>();
        private boolean useAdaGrad = false;
        private boolean fetchLabels = false;

        public Builder() {

        }

        public Builder<T> useAdaGrad(boolean useAdaGrad) {
            this.useAdaGrad = useAdaGrad;
            return this;
        }

        public Builder<T> setTargetVocabCache(@NonNull VocabCache cache) {
            this.cache = cache;
            return this;
        }

        public Builder<T> addSource(@NonNull SequenceIterator<T> iterator, int minElementFrequency) {
            sources.add(new VocabSource<T>(iterator, minElementFrequency));
            return this;
        }
/*
        public Builder<T> addSource(LabelAwareIterator iterator, int minWordFrequency) {
            sources.add(new VocabSource(iterator, minWordFrequency));
            return this;
        }

        public Builder<T> addSource(SentenceIterator iterator, int minWordFrequency) {
            sources.add(new VocabSource(new SentenceIteratorConverter(iterator), minWordFrequency));
            return this;
        }
        */
/*
        public Builder setTokenizerFactory(@NonNull TokenizerFactory factory) {
            this.tokenizerFactory = factory;
            return this;
        }
*/
        public Builder<T> setStopWords(@NonNull List<String> stopWords) {
            this.stopWords = stopWords;
            return this;
        }

        /**
         * Sets, if labels should be fetched, during vocab building
         *
         * @param reallyFetch
         * @return
         */
        public Builder<T> fetchLabels(boolean reallyFetch) {
            this.fetchLabels = reallyFetch;
            return this;
        }

        public VocabConstructor<T> build() {
            VocabConstructor<T> constructor = new VocabConstructor<T>();
            constructor.sources = this.sources;
            constructor.cache = this.cache;
            constructor.stopWords = this.stopWords;
            constructor.useAdaGrad = this.useAdaGrad;
            constructor.fetchLabels = this.fetchLabels;

            return constructor;
        }
    }

    @Data
    private static class VocabSource<T extends SequenceElement> {
        @NonNull private SequenceIterator<T> iterator;
        @NonNull private int minWordFrequency;
    }
}
